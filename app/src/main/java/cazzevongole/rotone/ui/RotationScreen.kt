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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cazzevongole.rotone.data.PlayMode
import cazzevongole.rotone.data.RotationState
import cazzevongole.rotone.data.Tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationScreen(
    state: RotationState,
    accessGranted: Boolean,
    notice: String?,
    onDismissNotice: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onModeChange: (PlayMode) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Tone) -> Unit,
    onPreview: (Tone) -> Unit,
    onResetPointer: () -> Unit,
    onOpenAccessSettings: () -> Unit,
    onPickSystemSound: () -> Unit,
    onPickFile: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onDismissNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RoTone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = statusLine(state),
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

            item { RotationCard(state, onModeChange, onResetPointer) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sounds",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (state.tones.isEmpty()) "none yet" else "${state.tones.size} in rotation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.tones.isEmpty()) {
                item { EmptyCard() }
            } else {
                itemsIndexed(
                    items = state.tones,
                    key = { _, tone -> tone.id },
                ) { index, tone ->
                    ToneRow(
                        position = index,
                        tone = tone,
                        isNext = index == state.nextIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.tones.lastIndex,
                        onMove = { delta -> onMove(index, delta) },
                        onPreview = { onPreview(tone) },
                        onRemove = { onRemove(tone) },
                    )
                }
            }

            item { AddButtons(onPickSystemSound, onPickFile) }
            item { HintCard() }
        }
    }
}

private fun statusLine(state: RotationState): String = when {
    !state.enabled -> "Paused"
    state.tones.isEmpty() -> "Add sounds to start rotating"
    state.mode == PlayMode.SHUFFLE -> "Shuffling ${state.tones.size} sounds"
    else -> "Next: ${state.nextTone?.name ?: "-"}"
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
                text = "Notification access is off",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "Android only lets an app react to notifications once you allow it. " +
                    "Open the settings, find RoTone in the list and turn it on.",
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
                Text("Open access settings")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RotationCard(
    state: RotationState,
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
                    selected = state.mode == PlayMode.IN_ORDER,
                    onClick = { onModeChange(PlayMode.IN_ORDER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("In order") },
                )
                SegmentedButton(
                    selected = state.mode == PlayMode.SHUFFLE,
                    onClick = { onModeChange(PlayMode.SHUFFLE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Shuffle") },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "PLAYS NEXT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.nextTone?.name ?: "No sounds yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onResetPointer,
                    enabled = state.nextIndex != 0,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Restart")
                }
            }
        }
    }
}

@Composable
private fun EmptyCard() {
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
                text = "No sounds in the rotation",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Add notification ringtones or your own audio files below.",
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
                        text = "plays next",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            RowAction(Icons.Filled.PlayArrow, "Preview ${tone.name}", true, onPreview)
            RowAction(Icons.Filled.KeyboardArrowUp, "Move up", canMoveUp) { onMove(-1) }
            RowAction(Icons.Filled.KeyboardArrowDown, "Move down", canMoveDown) { onMove(1) }
            RowAction(Icons.Filled.Delete, "Remove ${tone.name}", true, onRemove)
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
            Text("System sound")
        }
        OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
            Text("Audio file")
        }
    }
}

@Composable
private fun HintCard() {
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
                text = "How it works",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Hint("Android does not let one app change another app's notification sound, so RoTone plays the tone itself each time a notification arrives.")
            Hint("Set the apps you care about to Silent once, and you will hear only the rotating tone.")
            Hint("Silent, vibrate and Do Not Disturb stay quiet: the pointer only moves when a tone actually plays.")
            Hint("Every picked sound is copied into the app, so it keeps working if the original is deleted.")
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
