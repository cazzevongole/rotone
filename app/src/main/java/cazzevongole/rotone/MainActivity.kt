// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import cazzevongole.rotone.audio.TonePlayer
import cazzevongole.rotone.data.AppRule
import cazzevongole.rotone.data.KnownPackage
import cazzevongole.rotone.data.RotationState
import cazzevongole.rotone.data.Tone
import cazzevongole.rotone.data.ToneList
import cazzevongole.rotone.data.ToneStore
import cazzevongole.rotone.ui.RoToneTheme
import cazzevongole.rotone.ui.RotationScreen

class MainActivity : ComponentActivity() {

    private lateinit var store: ToneStore
    private lateinit var player: TonePlayer

    private var state by mutableStateOf(RotationState())
    private var accessGranted by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)

    /** null edits the fallback rotation, a package name edits that app's own rotation. */
    private var scope by mutableStateOf<String?>(null)

    /** The listener runs in this same process, so its saved pointers show up here immediately. */
    private val preferenceWatcher =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ToneStore.KEY_STATE) state = store.load()
        }

    private val systemSoundPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val uri = IntentCompat.getParcelableExtra(
                data,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            ) ?: return@registerForActivityResult
            importSound(uri)
        }

    private val audioFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importSound(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ToneStore(this)
        player = TonePlayer(this)
        state = store.load()
        accessGranted = hasNotificationAccess()

        setContent {
            RoToneTheme {
                RotationScreen(
                    state = state,
                    scope = scope,
                    accessGranted = accessGranted,
                    notice = notice,
                    onDismissNotice = { notice = null },
                    onToggleEnabled = { save(state.copy(enabled = it)) },
                    onSelectScope = { scope = it },
                    onAddApp = ::addApp,
                    onRemoveApp = ::removeApp,
                    onToggleApp = ::toggleApp,
                    onModeChange = { mode -> updateScoped { it.copy(mode = mode) } },
                    onMove = ::move,
                    onRemoveTone = ::removeTone,
                    onPreview = { player.play(store.fileFor(it)) },
                    onResetPointer = { updateScoped { it.copy(nextIndex = 0, lastPlayedIndex = -1) } },
                    onOpenAccessSettings = ::openAccessSettings,
                    onPickSystemSound = ::pickSystemSound,
                    onPickFile = ::pickAudioFile,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        store.preferences.registerOnSharedPreferenceChangeListener(preferenceWatcher)
        state = store.load()
        accessGranted = hasNotificationAccess()
        if (scope != null && state.apps.none { it.packageName == scope }) scope = null
    }

    override fun onStop() {
        store.preferences.unregisterOnSharedPreferenceChangeListener(preferenceWatcher)
        player.stop()
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun save(updated: RotationState) {
        store.save(updated)
        state = updated
    }

    private fun currentRotation(): ToneList {
        val packageName = scope ?: return state.fallback
        return state.apps.firstOrNull { it.packageName == packageName }?.rotation ?: state.fallback
    }

    /** Applies [transform] to the rotation of the currently selected scope. */
    private fun updateScoped(transform: (ToneList) -> ToneList) {
        val packageName = scope
        if (packageName == null || state.apps.none { it.packageName == packageName }) {
            save(state.copy(fallback = transform(state.fallback)))
            return
        }
        save(
            state.copy(
                apps = state.apps.map {
                    if (it.packageName == packageName) it.copy(rotation = transform(it.rotation)) else it
                },
            ),
        )
    }

    private fun addApp(candidate: KnownPackage) {
        if (state.apps.any { it.packageName == candidate.packageName }) {
            scope = candidate.packageName
            return
        }
        save(state.copy(apps = state.apps + AppRule(candidate.packageName, candidate.label)))
        scope = candidate.packageName
    }

    private fun removeApp(packageName: String) {
        save(state.copy(apps = state.apps.filterNot { it.packageName == packageName }))
        if (scope == packageName) scope = null
    }

    private fun toggleApp(packageName: String, enabled: Boolean) {
        save(
            state.copy(
                apps = state.apps.map {
                    if (it.packageName == packageName) it.copy(enabled = enabled) else it
                },
            ),
        )
    }

    private fun move(index: Int, delta: Int) {
        val tones = currentRotation().tones.toMutableList()
        val target = index + delta
        if (index !in tones.indices || target !in tones.indices) return
        val moving = tones[index]
        tones[index] = tones[target]
        tones[target] = moving
        updateScoped { it.copy(tones = tones) }
    }

    private fun removeTone(tone: Tone) {
        val rotation = currentRotation()
        val index = rotation.tones.indexOfFirst { it.id == tone.id }
        if (index < 0) return

        val tones = rotation.tones.toMutableList().apply { removeAt(index) }
        var next = rotation.nextIndex
        if (index < next) next -= 1
        next = if (tones.isEmpty()) 0 else next.mod(tones.size)
        var lastPlayed = rotation.lastPlayedIndex
        lastPlayed = when {
            tones.isEmpty() -> -1
            index == lastPlayed -> -1
            index < lastPlayed -> lastPlayed - 1
            else -> lastPlayed
        }
        store.deleteFile(tone)
        updateScoped { it.copy(tones = tones, nextIndex = next, lastPlayedIndex = lastPlayed) }
    }

    private fun importSound(uri: Uri) {
        Thread {
            val tone = store.import(uri, null)
            runOnUiThread {
                if (tone == null) {
                    notice = getString(R.string.notice_sound_failed)
                } else {
                    updateScoped { it.copy(tones = it.tones + tone) }
                    notice = getString(R.string.notice_sound_added, tone.name)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun pickSystemSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION or
                    RingtoneManager.TYPE_RINGTONE or
                    RingtoneManager.TYPE_ALARM,
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.picker_title))
        }
        runCatching { systemSoundPicker.launch(intent) }
            .onFailure { notice = getString(R.string.notice_no_sound_picker) }
    }

    private fun pickAudioFile() {
        runCatching { audioFilePicker.launch(arrayOf("audio/*")) }
            .onFailure { notice = getString(R.string.notice_no_file_picker) }
    }

    private fun openAccessSettings() {
        val listenerSettings = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (runCatching { startActivity(listenerSettings) }.isFailure) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
