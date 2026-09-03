package app.gamenative.ui.component.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.gamenative.R
import app.gamenative.ui.component.GradientProgressBar
import app.gamenative.ui.component.focusRing
import app.gamenative.ui.component.topbar.BackButton
import app.gamenative.ui.data.Achievement
import app.gamenative.ui.theme.PluviaTheme
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.delay

// Shared grayscale filter for locked achievement icons.
internal val grayMatrix = ColorMatrix().apply { setToSaturation(0f) }

// Steam leaves some localized names blank.
internal val Achievement.label: String
    get() = displayName.ifEmpty { name ?: "" }

internal fun Achievement.previewIconUrl(): String? =
    if (isUnlocked) icon.ifEmpty { iconGray } else iconGray ?: icon.ifEmpty { null }

// A still-locked secret achievement, whose details Steam keeps hidden.
internal val Achievement.isHiddenLocked: Boolean
    get() = hidden && !isUnlocked

// Achievement icon, grayed while locked. Pass masked = true to hide the art of a secret achievement.
@Composable
internal fun AchievementIcon(ach: Achievement, size: Dp, corner: Dp, masked: Boolean = false) {
    val box = Modifier
        .size(size)
        .clip(RoundedCornerShape(corner))
        .background(MaterialTheme.colorScheme.surfaceContainer)
    if (masked) {
        Box(box, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    } else {
        val iconUrl = ach.previewIconUrl()
        CoilImage(
            imageModel = { iconUrl ?: "" },
            imageOptions = ImageOptions(
                contentScale = ContentScale.Crop,
                contentDescription = ach.label,
                colorFilter = if (ach.isUnlocked) null else ColorFilter.colorMatrix(grayMatrix),
            ),
            modifier = box,
        )
    }
}

// Progress bar plus "current / max" for stat-linked achievements.
@Composable
private fun AchievementProgressBar(current: Float, max: Float, textStyle: TextStyle) {
    val fraction = if (max > 0f) (current / max).coerceIn(0f, 1f) else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GradientProgressBar(
            progress = fraction,
            modifier = Modifier.weight(1f),
            height = 5.dp,
        )
        Text(
            text = "${current.toInt()} / ${max.toInt()}",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// Focusable, clickable achievement row.
@Composable
internal fun AchievementRow(
    ach: Achievement,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusRing(interactionSource, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AchievementIcon(ach = ach, size = 40.dp, corner = 6.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ach.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ach.description.isNotEmpty()) {
                    Text(
                        text = ach.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val unlockedAt = ach.getFormattedUnlockDateTime()
                if (ach.isUnlocked && unlockedAt != null) {
                    Text(
                        text = stringResource(R.string.achievements_unlocked_at, unlockedAt.first, unlockedAt.second),
                        style = MaterialTheme.typography.labelSmall,
                        color = PluviaTheme.colors.statusInstalled,
                    )
                } else if (ach.hasProgress) {
                    AchievementProgressBar(
                        current = ach.progressCurrent ?: 0f,
                        max = ach.progressMax ?: 0f,
                        textStyle = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// Collapsed row standing in for still-locked secret achievements.
@Composable
private fun HiddenAchievementsSummary(count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusRing(interactionSource, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$count",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.achievements_hidden_remaining, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.achievements_hidden_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Full details for one achievement.
@Composable
internal fun AchievementDetailDialog(ach: Achievement, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        icon = {
            AchievementIcon(ach = ach, size = 64.dp, corner = 10.dp)
        },
        title = {
            Text(
                text = ach.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ach.description.isNotEmpty()) {
                    Text(
                        text = ach.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val unlockedAt = ach.getFormattedUnlockDateTime()
                if (ach.isUnlocked && unlockedAt != null) {
                    Text(
                        text = stringResource(R.string.achievements_unlocked_at, unlockedAt.first, unlockedAt.second),
                        style = MaterialTheme.typography.labelMedium,
                        color = PluviaTheme.colors.statusInstalled,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (ach.hasProgress) {
                    AchievementProgressBar(
                        current = ach.progressCurrent ?: 0f,
                        max = ach.progressMax ?: 0f,
                        textStyle = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.achievements_locked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

// Window-less achievements list. Hosted either by [AchievementsDialog] (details screen) or by a
// navigation dialog destination (in-game overlay). Owns hidden-reveal and per-achievement detail.
@Composable
internal fun AchievementsListContent(
    achievements: List<Achievement>,
    visibleState: MutableTransitionState<Boolean>,
    onRequestDismiss: () -> Unit,
) {
    // Secret achievements collapse into one row until revealed. Reveal is session-only.
    val (hiddenLocked, visibleAchievements) = remember(achievements) {
        achievements.partition { it.isHiddenLocked }
    }
    var revealHidden by remember { mutableStateOf(false) }
    var showRevealConfirm by remember { mutableStateOf(false) }
    var detailAchievement by remember { mutableStateOf<Achievement?>(null) }
    // Keep focus on the freshly revealed achievements instead of jumping to the list top.
    val revealedFocusRequester = remember { FocusRequester() }
    LaunchedEffect(revealHidden) {
        if (revealHidden) {
            repeat(5) {
                try {
                    if (revealedFocusRequester.requestFocus()) return@LaunchedEffect
                } catch (_: IllegalStateException) {
                }
                delay(32)
            }
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(200)) +
            slideInVertically(animationSpec = tween(200)) { it / 12 },
        exit = fadeOut(animationSpec = tween(150)) +
            slideOutVertically(animationSpec = tween(150)) { it / 12 },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .navigationBarsPadding(),
            ) {
                // Header: back + title, mirroring the screenshot gallery.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackButton(onClick = onRequestDismiss)
                    Text(
                        text = stringResource(R.string.achievements_all_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleAchievements) { ach ->
                        AchievementRow(ach) { detailAchievement = ach }
                    }
                    if (hiddenLocked.isNotEmpty()) {
                        if (revealHidden) {
                            itemsIndexed(hiddenLocked) { index, ach ->
                                AchievementRow(
                                    ach = ach,
                                    focusRequester = if (index == 0) revealedFocusRequester else null,
                                ) { detailAchievement = ach }
                            }
                        } else {
                            item {
                                HiddenAchievementsSummary(count = hiddenLocked.size) {
                                    showRevealConfirm = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRevealConfirm) {
        AlertDialog(
            onDismissRequest = { showRevealConfirm = false },
            title = { Text(stringResource(R.string.achievements_reveal_title)) },
            text = { Text(stringResource(R.string.achievements_reveal_message)) },
            confirmButton = {
                TextButton(onClick = {
                    revealHidden = true
                    showRevealConfirm = false
                }) { Text(stringResource(R.string.achievements_reveal_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRevealConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    detailAchievement?.let { ach ->
        AchievementDetailDialog(ach) { detailAchievement = null }
    }
}

@Composable
internal fun AchievementsDialog(
    achievements: List<Achievement>,
    onDismiss: () -> Unit,
) {
    // Fade/slide the content in and play the exit before dismissing, matching the screenshot gallery.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visibleState.isIdle) {
        if (visibleState.isIdle && !visibleState.currentState) onDismiss()
    }
    val dismiss = { visibleState.targetState = false }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Drop the window dim so the entrance animation has no scrim flash.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        AchievementsListContent(
            achievements = achievements,
            visibleState = visibleState,
            onRequestDismiss = dismiss,
        )
    }
}
