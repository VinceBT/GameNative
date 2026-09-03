package app.gamenative.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.dialog.AchievementDetailDialog
import app.gamenative.ui.component.dialog.AchievementRow
import app.gamenative.ui.data.Achievement
import app.gamenative.ui.theme.PluviaTheme

/** Number of achievements previewed inline in the pause menu; the rest are in the full modal. */
private const val RECENT_COUNT = 5

@Composable
fun AchievementsQuickMenuTab(
    achievements: List<Achievement>,
    onViewAll: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val recent = remember(achievements) { achievements.take(RECENT_COUNT) }
    var detailAchievement by remember { mutableStateOf<Achievement?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.achievements_recent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        recent.forEachIndexed { index, ach ->
            AchievementRow(
                ach = ach,
                focusRequester = if (index == 0) firstItemFocusRequester else null,
                modifier = Modifier.padding(horizontal = 8.dp),
                onClick = { detailAchievement = ach },
            )
        }

        AccentActionRow(
            title = stringResource(R.string.achievements_all_title),
            icon = Icons.Default.EmojiEvents,
            accentColor = PluviaTheme.colors.accentPurple,
            onClick = onViewAll,
        )
    }

    detailAchievement?.let { ach ->
        AchievementDetailDialog(ach) { detailAchievement = null }
    }
}
