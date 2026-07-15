# Achievements Quick-Menu Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Achievements tab to the in-game quick menu that appears only when the running game has achievements and opens the same full-screen achievements modal used on the game details screen.

**Architecture:** Extract the existing achievements modal (`AchievementsDialog` + helpers) out of `LibraryAppScreen.kt` into a shared file so both the details screen and the in-game overlay can use it. Add a new `AchievementsQuickMenuTab` pane composable mirroring `ScreenshotsQuickMenuTab`. Wire a gated rail tab into `QuickMenu.kt`, and fetch/gate/render from `XServerScreen.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit (unit tests in `app/src/test`).

## Global Constraints

- Language: Kotlin. UI: Jetpack Compose. Module: `app`.
- Steam achievement fetch contract (`SteamService.fetchAchievementsForDisplay(appId: Int): List<Achievement>?`): `null` = fetch failed / not connected / timeout; empty list = game genuinely has no achievements; non-empty = has achievements. The tab is shown only for a non-null, non-empty list.
- `Achievement` data class lives at `app/src/main/java/app/gamenative/ui/data/Achievement.kt`; all fields public. Constructor params in order: `displayName: String, name: String?, isUnlocked: Boolean, description: String, unlockTimestamp: Int, hidden: Boolean, icon: String, iconGray: String?, progressCurrent: Float? = null, progressMax: Float? = null`.
- Extraction MUST NOT change behavior on the game details screen.
- Only one new string resource: `achievements_tab`. Reuse `achievements_all_title` for the "view all" action label and `achievements_total` for the counter sublabel.
- Commit identity: `VinceBT <vincebt06@gmail.com>`. No `Co-Authored-By` trailer. Terse commit subjects.

---

### Task 1: Pure display helpers (predicate + sort) with unit tests

Extracts the gating predicate and the display sort into a plain-Kotlin, JVM-testable file. No Compose imports so it unit-tests without Robolectric.

**Files:**
- Create: `app/src/main/java/app/gamenative/ui/data/AchievementDisplay.kt`
- Test: `app/src/test/java/app/gamenative/ui/data/AchievementDisplayTest.kt`

**Interfaces:**
- Produces:
  - `fun hasDisplayableAchievements(achievements: List<Achievement>?): Boolean`
  - `fun List<Achievement>.sortedForDisplay(): List<Achievement>` (unlocked first, then most-recently-unlocked first)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/gamenative/ui/data/AchievementDisplayTest.kt`:

```kotlin
package app.gamenative.ui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementDisplayTest {

    private fun ach(
        name: String,
        unlocked: Boolean = false,
        ts: Int = 0,
    ) = Achievement(
        displayName = name,
        name = name,
        isUnlocked = unlocked,
        description = "",
        unlockTimestamp = ts,
        hidden = false,
        icon = "",
        iconGray = null,
    )

    @Test
    fun hasDisplayable_nullIsFalse() {
        assertFalse(hasDisplayableAchievements(null))
    }

    @Test
    fun hasDisplayable_emptyIsFalse() {
        assertFalse(hasDisplayableAchievements(emptyList()))
    }

    @Test
    fun hasDisplayable_nonEmptyIsTrue() {
        assertTrue(hasDisplayableAchievements(listOf(ach("a"))))
    }

    @Test
    fun sorted_unlockedBeforeLocked_thenNewestFirst() {
        val locked = ach("locked", unlocked = false, ts = 0)
        val oldUnlock = ach("old", unlocked = true, ts = 100)
        val newUnlock = ach("new", unlocked = true, ts = 200)
        val result = listOf(locked, oldUnlock, newUnlock).sortedForDisplay()
        assertEquals(listOf("new", "old", "locked"), result.map { it.displayName })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.gamenative.ui.data.AchievementDisplayTest"`
Expected: FAIL — `AchievementDisplay.kt` does not exist / unresolved references `hasDisplayableAchievements`, `sortedForDisplay`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/app/gamenative/ui/data/AchievementDisplay.kt`:

```kotlin
package app.gamenative.ui.data

/** True when there is at least one achievement to display. `null` (fetch failed / offline) is false. */
fun hasDisplayableAchievements(achievements: List<Achievement>?): Boolean =
    !achievements.isNullOrEmpty()

/** Unlocked first, then most-recently-unlocked first. */
fun List<Achievement>.sortedForDisplay(): List<Achievement> =
    sortedWith(
        compareByDescending<Achievement> { it.isUnlocked }
            .thenByDescending { it.unlockTimestamp },
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.gamenative.ui.data.AchievementDisplayTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/gamenative/ui/data/AchievementDisplay.kt app/src/test/java/app/gamenative/ui/data/AchievementDisplayTest.kt
git -c user.name='VinceBT' -c user.email='vincebt06@gmail.com' commit -m "Add achievement display predicate and sort helpers"
```

---

### Task 2: Extract the achievements modal into a shared file

Pure move refactor. The details screen must render identically afterward. This unblocks reuse from the quick menu.

**Files:**
- Create: `app/src/main/java/app/gamenative/ui/component/dialog/AchievementsDialog.kt`
- Modify: `app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt`

**Interfaces:**
- Consumes: `List<Achievement>.sortedForDisplay()` (Task 1).
- Produces (in package `app.gamenative.ui.component.dialog`):
  - `internal fun AchievementsDialog(achievements: List<Achievement>, onDismiss: () -> Unit)` (@Composable)
  - `internal fun AchievementIcon(ach: Achievement, size: Dp, corner: Dp, masked: Boolean = false)` (@Composable)
  - `internal val Achievement.isHiddenLocked: Boolean`
  - `internal fun Achievement.previewIconUrl(): String?`
  - `internal val grayMatrix: ColorMatrix`

- [ ] **Step 1: Create the new file with the moved declarations**

Create `app/src/main/java/app/gamenative/ui/component/dialog/AchievementsDialog.kt`. Move these declarations **verbatim** from `LibraryAppScreen.kt` (do NOT move `AchievementsRow`, which stays):

- `grayMatrix` (currently `LibraryAppScreen.kt:1505`) — change `private` → `internal`
- `Achievement.previewIconUrl()` (`:1507`) — change `private` → `internal`
- `Achievement.isHiddenLocked` (`:1511`) — change `private` → `internal`
- `AchievementIcon` (`:1516`) — change `private` → `internal`
- `AchievementProgressBar` (`:1546`) — keep `private`
- `AchievementRow` (`:1571`) — keep `private`
- `HiddenAchievementsSummary` (`:1629`) — keep `private`
- `AchievementDetailDialog` (`:1683`) — keep `private`
- `AchievementsDialog` (`:1871`, ends at the closing brace on `:2005`) — change `private` → `internal`

The new file's package declaration:

```kotlin
package app.gamenative.ui.component.dialog
```

Add these project/library imports at the top (the moved code needs them; the compiler will flag any remaining Compose stdlib imports — copy those from `LibraryAppScreen.kt`'s import block as needed):

```kotlin
import app.gamenative.R
import app.gamenative.ui.component.focusRing
import app.gamenative.ui.component.topbar.BackButton
import app.gamenative.ui.data.Achievement
import app.gamenative.ui.theme.PluviaTheme
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.delay
```

- [ ] **Step 2: Remove the moved declarations from `LibraryAppScreen.kt`**

Delete the exact declarations listed in Step 1 from `LibraryAppScreen.kt` (leave `AchievementsRow` at `:1744-1868` in place). After deletion, `LibraryAppScreen.kt` still references `AchievementsDialog`, `AchievementIcon`, `grayMatrix`, `previewIconUrl`, `isHiddenLocked` from inside `AchievementsRow` — those now resolve cross-file via the new `internal` declarations.

Add these imports to `LibraryAppScreen.kt`:

```kotlin
import app.gamenative.ui.component.dialog.AchievementsDialog
import app.gamenative.ui.component.dialog.AchievementIcon
import app.gamenative.ui.component.dialog.isHiddenLocked
import app.gamenative.ui.component.dialog.previewIconUrl
import app.gamenative.ui.component.dialog.grayMatrix
import app.gamenative.ui.data.sortedForDisplay
```

Remove any imports in `LibraryAppScreen.kt` that are now unused only by the moved code (the compiler will flag unused imports as warnings, not errors — safe to clean up but not required to build).

- [ ] **Step 3: Update `AchievementsRow` to use the shared sort helper (DRY)**

In `LibraryAppScreen.kt`, `AchievementsRow` currently computes the sort inline (`:1753-1756`):

```kotlin
    val sortedAchievements = achievements.sortedWith(
        compareByDescending<Achievement> { it.isUnlocked }
            .thenByDescending { it.unlockTimestamp },
    )
```

Replace with:

```kotlin
    val sortedAchievements = achievements.sortedForDisplay()
```

- [ ] **Step 4: Build to verify the move compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If there are unresolved-reference errors, they will be Compose/Android imports missing from the new file — add each flagged import (copy from `LibraryAppScreen.kt`'s import block) and rebuild.

- [ ] **Step 5: Manually verify the details screen is unchanged**

Launch the app, open a game with achievements from the library, confirm the achievements card renders, tap it, confirm the full-screen modal opens with rows, hidden-collapse, and detail dialog exactly as before.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/gamenative/ui/component/dialog/AchievementsDialog.kt app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt
git -c user.name='VinceBT' -c user.email='vincebt06@gmail.com' commit -m "Extract achievements modal into shared dialog file"
```

---

### Task 3: Add the `AchievementsQuickMenuTab` pane composable + string

The pane content shown when the Achievements tab is selected. Mirrors `ScreenshotsQuickMenuTab`.

**Files:**
- Create: `app/src/main/java/app/gamenative/ui/component/AchievementsQuickMenuTab.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AchievementIcon`, `isHiddenLocked` (Task 2); `Achievement` (data class).
- Produces: `fun AchievementsQuickMenuTab(achievements: List<Achievement>, onViewAll: () -> Unit, firstItemFocusRequester: FocusRequester? = null, modifier: Modifier = Modifier)` (@Composable). Caller passes an already-sorted list.

- [ ] **Step 1: Add the string resource**

In `app/src/main/res/values/strings.xml`, after line `<string name="achievements">Achievements</string>` (`:2108`), add:

```xml
    <string name="achievements_tab">Achievements</string>
```

- [ ] **Step 2: Create the pane composable**

Create `app/src/main/java/app/gamenative/ui/component/AchievementsQuickMenuTab.kt`:

```kotlin
package app.gamenative.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.dialog.AchievementIcon
import app.gamenative.ui.component.dialog.isHiddenLocked
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
    val unlocked = remember(achievements) { achievements.count { it.isUnlocked } }
    val total = achievements.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = "$unlocked / $total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.achievements_total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

        recent.forEachIndexed { index, ach ->
            val rowShape = RoundedCornerShape(8.dp)
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(rowShape)
                    .focusRing(interaction, rowShape)
                    .then(
                        if (index == 0 && firstItemFocusRequester != null) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                    ) { onViewAll() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AchievementIcon(
                    ach = ach,
                    size = 40.dp,
                    corner = 8.dp,
                    masked = ach.isHiddenLocked,
                )
                Text(
                    text = ach.displayName ?: ach.name ?: "",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AccentActionRow(
            title = stringResource(R.string.achievements_all_title),
            icon = Icons.Default.EmojiEvents,
            accentColor = PluviaTheme.colors.accentPurple,
            onClick = onViewAll,
        )
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/gamenative/ui/component/AchievementsQuickMenuTab.kt app/src/main/res/values/strings.xml
git -c user.name='VinceBT' -c user.email='vincebt06@gmail.com' commit -m "Add achievements quick-menu pane composable"
```

---

### Task 4: Wire the Achievements tab into `QuickMenu.kt`

Adds the tab id, focus requester, gated rail button, title mapping, pane dispatch, focus dispatch, last-tab fallback, and new params. All edits in `app/src/main/java/app/gamenative/ui/component/QuickMenu.kt`.

**Files:**
- Modify: `app/src/main/java/app/gamenative/ui/component/QuickMenu.kt`

**Interfaces:**
- Consumes: `AchievementsQuickMenuTab(...)` (Task 3); `Achievement` (data class).
- Produces: three new `QuickMenu(...)` params — `achievements: List<Achievement> = emptyList()`, `hasAchievements: Boolean = false`, `onOpenAchievements: () -> Unit = {}`.

- [ ] **Step 1: Add the tab id constant**

In the `QuickMenuTab` object (`QuickMenu.kt:117-124`), after `const val SCREENSHOTS = 5`, add:

```kotlin
    const val ACHIEVEMENTS = 6
```

- [ ] **Step 2: Add imports and the three new `QuickMenu` params**

Add imports near the other `app.gamenative` imports:

```kotlin
import androidx.compose.material.icons.filled.EmojiEvents
import app.gamenative.ui.data.Achievement
```

In the `QuickMenu(...)` parameter list, after `onOpenScreenshotViewer: (index: Int) -> Unit = {},` (`:278`), add:

```kotlin
    achievements: List<Achievement> = emptyList(),
    hasAchievements: Boolean = false,
    onOpenAchievements: () -> Unit = {},
```

- [ ] **Step 3: Add the focus requester**

After `val screenshotsItemFocusRequester = remember { FocusRequester() }` (`:379`), add:

```kotlin
    val achievementsItemFocusRequester = remember { FocusRequester() }
```

- [ ] **Step 4: Guard the restored last-tab against a hidden Achievements tab**

Replace the `selectedTab` initializer (`:349-355`):

```kotlin
    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            if (PrefManager.quickMenuLastTab == QuickMenuTab.LSFG && !isLsfgAvailable)
                QuickMenuTab.HUD
            else PrefManager.quickMenuLastTab
        )
    }
```

with:

```kotlin
    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            when {
                PrefManager.quickMenuLastTab == QuickMenuTab.LSFG && !isLsfgAvailable -> QuickMenuTab.HUD
                PrefManager.quickMenuLastTab == QuickMenuTab.ACHIEVEMENTS && !hasAchievements -> QuickMenuTab.CONTROLLER
                else -> PrefManager.quickMenuLastTab
            },
        )
    }
```

- [ ] **Step 5: Add the tab title mapping**

In the `selectedTabLabelResId` `when` (`:356-363`), after `QuickMenuTab.SCREENSHOTS -> R.string.screenshots_tab`, add:

```kotlin
        QuickMenuTab.ACHIEVEMENTS -> R.string.achievements_tab
```

- [ ] **Step 6: Add the gated rail button**

Immediately after the Screenshots `QuickMenuTabButton` block (closes at `:553`), add:

```kotlin
                                if (hasAchievements) {
                                    QuickMenuTabButton(
                                        icon = Icons.Default.EmojiEvents,
                                        contentDescriptionResId = R.string.achievements_tab,
                                        selected = selectedTab == QuickMenuTab.ACHIEVEMENTS,
                                        accentColor = PluviaTheme.colors.accentPurple,
                                        onSelected = {
                                            selectedTab = QuickMenuTab.ACHIEVEMENTS
                                            PrefManager.quickMenuLastTab = selectedTab
                                        },
                                        modifier = Modifier.width(56.dp),
                                        focusRequester = achievementsItemFocusRequester,
                                    )
                                }
```

- [ ] **Step 7: Add the pane dispatch branch**

After the `QuickMenuTab.SCREENSHOTS -> { ScreenshotsQuickMenuTab(...) }` branch (closes at `:690`), add:

```kotlin
                                    QuickMenuTab.ACHIEVEMENTS -> {
                                        AchievementsQuickMenuTab(
                                            achievements = achievements,
                                            onViewAll = onOpenAchievements,
                                            firstItemFocusRequester = achievementsItemFocusRequester,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
```

- [ ] **Step 8: Add the focus dispatch branch**

In the focus `when` (`:747-752`), after `QuickMenuTab.SCREENSHOTS -> screenshotsItemFocusRequester.requestFocus()`, add:

```kotlin
                        QuickMenuTab.ACHIEVEMENTS -> achievementsItemFocusRequester.requestFocus()
```

- [ ] **Step 9: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/app/gamenative/ui/component/QuickMenu.kt
git -c user.name='VinceBT' -c user.email='vincebt06@gmail.com' commit -m "Wire achievements tab into quick menu"
```

---

### Task 5: Fetch, gate, and render from `XServerScreen.kt`

Fetches achievements on game load, derives `hasAchievements`, passes the new params to `QuickMenu(...)`, and renders the shared modal over the overlay. All edits in `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`.

**Files:**
- Modify: `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`

**Interfaces:**
- Consumes: `QuickMenu(achievements, hasAchievements, onOpenAchievements)` (Task 4); `AchievementsDialog` (Task 2); `hasDisplayableAchievements`, `sortedForDisplay` (Task 1); `SteamService.fetchAchievementsForDisplay(Int)`.

- [ ] **Step 1: Add imports**

Add near the other `app.gamenative` imports in `XServerScreen.kt`:

```kotlin
import app.gamenative.ui.component.dialog.AchievementsDialog
import app.gamenative.ui.data.Achievement
import app.gamenative.ui.data.hasDisplayableAchievements
import app.gamenative.ui.data.sortedForDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
```

(If any of these are already imported, skip the duplicate — the compiler flags duplicate imports.)

- [ ] **Step 2: Add state**

Near the other quick-menu state declarations (e.g. after `var screenshotRefreshKey by remember { mutableStateOf(0) }` at `:554`), add:

```kotlin
    var achievements by remember(gameId) { mutableStateOf<List<Achievement>?>(null) }
    var showAchievementsDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Add the fetch effect**

Add a `LaunchedEffect` in the composable body (place it near the other top-level `LaunchedEffect`s in `XServerScreen`, after `gameId` is defined at `:433`):

```kotlin
    LaunchedEffect(gameId) {
        // null = fetch failed (empty = no achievements); retry so a transient Steam error
        // doesn't hide the tab on a game that actually has achievements.
        repeat(3) { attempt ->
            val result = try {
                withContext(Dispatchers.IO) {
                    SteamService.fetchAchievementsForDisplay(gameId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch achievements for %d", gameId)
                null
            }
            if (result != null) {
                achievements = result
                return@LaunchedEffect
            }
            if (attempt < 2) delay(2000)
        }
    }
```

- [ ] **Step 4: Pass the new params to `QuickMenu(...)`**

In the `QuickMenu(...)` call, after `onOpenScreenshotViewer = { index -> ... }` (closes at `:2617`), add:

```kotlin
            achievements = achievements.orEmpty().sortedForDisplay(),
            hasAchievements = hasDisplayableAchievements(achievements),
            onOpenAchievements = { showAchievementsDialog = true },
```

- [ ] **Step 5: Render the modal as a sibling after `QuickMenu(...)`**

Immediately after the `QuickMenu(...)` closing `)` (`:2618`), add:

```kotlin
        if (showAchievementsDialog) {
            AchievementsDialog(
                achievements = achievements.orEmpty().sortedForDisplay(),
                onDismiss = { showAchievementsDialog = false },
            )
        }
```

- [ ] **Step 6: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Install and manually verify end-to-end**

Build and install the universal APK (full install, not `installModernDebug`). Then:
1. Launch a game **with** achievements → open the quick menu → confirm the trophy tab appears in the rail → select it → confirm the pane shows the counter + recent rows + "All Achievements" row → tap it → confirm the same full-screen modal opens over the overlay → dismiss returns to the menu.
2. Launch a game **without** achievements (or while offline) → open the quick menu → confirm the trophy tab is absent and no other tab is broken.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt
git -c user.name='VinceBT' -c user.email='vincebt06@gmail.com' commit -m "Fetch and show achievements tab in-game"
```

---

## Notes on verification

- `./gradlew :app:testDebugUnitTest --tests "app.gamenative.ui.data.AchievementDisplayTest"` is the only automated test; everything else is compile + manual because the changes are Compose UI wiring and a file move with no new testable logic.
- The extraction (Task 2) is the highest-risk step: its correctness is "the details screen behaves identically." Do the manual check in Task 2 Step 5 before moving on.
