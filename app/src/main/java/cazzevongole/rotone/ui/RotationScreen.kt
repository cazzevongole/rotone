// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 cazzevongole

package cazzevongole.rotone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cazzevongole.rotone.R
import cazzevongole.rotone.data.AppRule
import cazzevongole.rotone.data.KnownPackage
import cazzevongole.rotone.data.PlayMode
import cazzevongole.rotone.data.RotationState
import cazzevongole.rotone.data.Tone
import cazzevongole.rotone.data.ToneList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(
    state: RotationState,
    scope: String?,
    accessGranted: Boolean,
    notice: String?,
    onDismissNotice: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectScope: (String?) -> Unit,
    onAddApp: (KnownPackage) -> Unit,
    onRemoveApp: (String) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onModeChange: (PlayMode) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemoveTone: (Tone) -> Unit,
    onPreview: (Tone) -> Unit,
    onResetPointer: () -> Unit,
    onOpenAccessSettings: () -> Unit,
    onPickSystemSound: () -> Unit,
    onPickFile: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val rule = scope?.let { packageName -> state.apps.firstOrNull { it.packageName == packageName } }
    val rotation = rule?.rotation ?: state.fallback
    val scopeTitle = rule?.label ?: stringResource(R.string.scope_all_apps)

    var scopeDialog by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onDismissNotice()
        }
    }

    if (scopeDialog) {
        ScopeDialog(
            state = state,
            scope = scope,
            onSelect = onSelectScope,
            onAddApp = { addDialog = true },
            onDismiss = { scopeDialog = false },
        )
    }
    if (addDialog) {
        AddAppDialog(
            candidates = state.knownPackages.filter { candidate ->
                state.apps.none { it.packageName == candidate.packageName }
            },
            onPick = onAddApp,
            onDismiss = { addDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = statusLine(state, rule, rotation),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!accessGranted) {
                item { AccessCard(onOpenAccessSettings) }
            }

            item {
                ScopeCard(
                    title = scopeTitle,
                    packageName = rule?.packageName,
                    rule = rule,
                    soundCount = rotation.tones.size,
                    onChangeScope = { scopeDialog = true },
                    onToggleApp = { enabled -> rule?.let { onToggleApp(it.packageName, enabled) } },
                )
            }

            item { RotationCard(rotation, onModeChange, onResetPointer) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.section_sounds),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (rotation.tones.isEmpty()) {
                            stringResource(R.string.sounds_none)
                        } else {
                            stringResource(R.string.sounds_in_rotation, rotation.tones.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (rotation.tones.isEmpty()) {
                item { EmptyCard(isAppScope = rule != null) }
            } else {
                itemsIndexed(
                    items = rotation.tones,
                    key = { _, tone -> tone.id },
                ) { index, tone ->
                    ToneRow(
                        position = index,
                        tone = tone,
                        isNext = index == rotation.nextIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < rotation.tones.lastIndex,
                        onMove = { delta -> onMove(index, delta) },
                        onPreview = { onPreview(tone) },
                        onRemove = { onRemoveTone(tone) },
                    )
                }
            }

            item { AddButtons(onPickSystemSound, onPickFile) }
            item {
                AppsCard(
                    state = state,
                    scope = scope,
                    onSelect = { packageName -> onSelectScope(packageName) },
                    onToggle = onToggleApp,
                    onRemove = onRemoveApp,
                )
            }
            item { HintCard(rule != null) }
        }
    }
}

@Composable
private fun statusLine(state: RotationState, rule: AppRule?, rotation: ToneList): String = when {
    !state.enabled -> stringResource(R.string.status_paused)
    rule != null && !rule.enabled -> stringResource(R.string.status_off_for, rule.label)
    rotation.tones.isEmpty() && rule != null ->
        stringResource(R.string.status_no_sounds_for, rule.label)
    rotation.tones.isEmpty() -> stringResource(R.string.status_add_sounds)
    rotation.mode == PlayMode.SHUFFLE ->
        pluralStringResource(R.plurals.status_shuffling, rotation.tones.size, rotation.tones.size)
    else -> stringResource(R.string.status_next, rotation.nextTone?.name ?: "-")
}

@Composable
private fun AccessCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.access_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.access_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.access_button))
            }
        }
    }
}

@Composable
private fun ScopeCard(
    title: String,
    packageName: String?,
    rule: AppRule?,
    soundCount: Int,
    onChangeScope: () -> Unit,
    onToggleApp: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_rotation_for),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (packageName != null) {
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onChangeScope) {
                    Text(stringResource(R.string.action_change))
                }
            }

            if (rule != null) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_rotate_this_app),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = if (soundCount == 0) {
                                stringResource(R.string.scope_follows_default)
                            } else {
                                pluralStringResource(
                                    R.plurals.scope_app_sounds,
                                    soundCount,
                                    soundCount,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = rule.enabled, onCheckedChange = onToggleApp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotationCard(
    rotation: ToneList,
    onModeChange: (PlayMode) -> Unit,
    onResetPointer: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = rotation.mode == PlayMode.IN_ORDER,
                    onClick = { onModeChange(PlayMode.IN_ORDER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.mode_in_order)) },
                )
                SegmentedButton(
                    selected = rotation.mode == PlayMode.SHUFFLE,
                    onClick = { onModeChange(PlayMode.SHUFFLE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.mode_shuffle)) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_plays_next),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = rotation.nextTone?.name ?: stringResource(R.string.no_sounds_yet),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onResetPointer,
                    enabled = rotation.nextIndex != 0,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_restart))
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(isAppScope: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    if (isAppScope) R.string.empty_body_app else R.string.empty_body_default,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToneRow(
    position: Int,
    tone: Tone,
    isNext: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isNext) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${position + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNext) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = tone.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isNext) {
                    Text(
                        text = stringResource(R.string.row_plays_next),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            RowAction(
                Icons.Filled.PlayArrow,
                stringResource(R.string.cd_preview, tone.name),
                true,
                onPreview,
            )
            RowAction(
                Icons.Filled.KeyboardArrowUp,
                stringResource(R.string.cd_move_up),
                canMoveUp,
            ) { onMove(-1) }
            RowAction(
                Icons.Filled.KeyboardArrowDown,
                stringResource(R.string.cd_move_down),
                canMoveDown,
            ) { onMove(1) }
            RowAction(
                Icons.Filled.Delete,
                stringResource(R.string.cd_remove_tone, tone.name),
                true,
                onRemove,
            )
        }
    }
}

@Composable
private fun RowAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AddButtons(onPickSystemSound: () -> Unit, onPickFile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onPickSystemSound, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_system_sound))
        }
        OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_audio_file))
        }
    }
}

@Composable
private fun AppsCard(
    state: RotationState,
    scope: String?,
    onSelect: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.title_per_app),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.apps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.apps.isEmpty()) {
                Text(
                    text = stringResource(R.string.per_app_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.apps.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(rule.packageName) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = rule.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (rule.packageName == scope) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    !rule.enabled -> stringResource(R.string.app_state_off)
                                    rule.rotation.tones.isEmpty() ->
                                        stringResource(R.string.app_state_follows_default)
                                    else -> pluralStringResource(
                                        R.plurals.app_row_sounds,
                                        rule.rotation.tones.size,
                                        rule.rotation.tones.size,
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule.packageName, it) },
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onRemove(rule.packageName) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(
                                    R.string.cd_remove_app,
                                    rule.label,
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeDialog(
    state: RotationState,
    scope: String?,
    onSelect: (String?) -> Unit,
    onAddApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_scope_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ScopeOption(
                    title = stringResource(R.string.scope_all_apps),
                    subtitle = stringResource(R.string.scope_default_list),
                    selected = scope == null,
                    onClick = {
                        onSelect(null)
                        onDismiss()
                    },
                )
                state.apps.forEach { rule ->
                    ScopeOption(
                        title = rule.label,
                        subtitle = rule.packageName,
                        selected = scope == rule.packageName,
                        onClick = {
                            onSelect(rule.packageName)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onAddApp()
                },
            ) {
                Text(stringResource(R.string.action_add_app))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun ScopeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddAppDialog(
    candidates: List<KnownPackage>,
    onPick: (KnownPackage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_app_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.add_app_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    candidates.forEach { candidate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onPick(candidate)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = candidate.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = candidate.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun HintCard(isAppScope: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.title_how_it_works),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (isAppScope) {
                Hint(stringResource(R.string.hint_app_scope))
            } else {
                Hint(stringResource(R.string.hint_fallback_scope))
            }
            Hint(stringResource(R.string.hint_android))
            Hint(stringResource(R.string.hint_silent))
            Hint(stringResource(R.string.hint_dnd))
            Hint(stringResource(R.string.hint_copies))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
