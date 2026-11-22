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
import cazzevongole.rotone.data.RotationState
import cazzevongole.rotone.data.Tone
import cazzevongole.rotone.data.ToneStore
import cazzevongole.rotone.ui.RoToneTheme
import cazzevongole.rotone.ui.RotationScreen

class MainActivity : ComponentActivity() {

    private lateinit var store: ToneStore
    private lateinit var player: TonePlayer

    private var state by mutableStateOf(RotationState())
    private var accessGranted by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)

    /** The listener runs in this same process, so its saved pointer shows up here immediately. */
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
                    accessGranted = accessGranted,
                    notice = notice,
                    onDismissNotice = { notice = null },
                    onToggleEnabled = { save(state.copy(enabled = it)) },
                    onModeChange = { save(state.copy(mode = it)) },
                    onMove = ::move,
                    onRemove = ::remove,
                    onPreview = { player.play(store.fileFor(it)) },
                    onResetPointer = { save(state.copy(nextIndex = 0)) },
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

    private fun move(index: Int, delta: Int) {
        val tones = state.tones.toMutableList()
        val target = index + delta
        if (index !in tones.indices || target !in tones.indices) return
        val moving = tones[index]
        tones[index] = tones[target]
        tones[target] = moving
        save(state.copy(tones = tones))
    }

    private fun remove(tone: Tone) {
        val index = state.tones.indexOfFirst { it.id == tone.id }
        if (index < 0) return
        val tones = state.tones.toMutableList().apply { removeAt(index) }
        var next = state.nextIndex
        if (index < next) next -= 1
        next = if (tones.isEmpty()) 0 else next.mod(tones.size)
        store.deleteFile(tone)
        save(state.copy(tones = tones, nextIndex = next))
    }

    private fun importSound(uri: Uri) {
        Thread {
            val tone = store.import(uri, null)
            runOnUiThread {
                if (tone == null) {
                    notice = "That sound could not be added."
                } else {
                    save(state.copy(tones = state.tones + tone))
                    notice = "Added ${tone.name}"
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
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pick a sound")
        }
        runCatching { systemSoundPicker.launch(intent) }
            .onFailure { notice = "No system sound picker on this device." }
    }

    private fun pickAudioFile() {
        runCatching { audioFilePicker.launch(arrayOf("audio/*")) }
            .onFailure { notice = "No file picker on this device." }
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
