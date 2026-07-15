# Achievements in the in-game Quick Menu — Design

## Goal

Add an Achievements tab to the in-game quick menu (the pause overlay shown while a
game runs). The tab:

- Appears **only** when the running game has achievements. If it has none, or the
  achievement list can't be fetched (offline), the tab does not appear at all.
- Opens the **same** full-screen achievements modal (`AchievementsDialog`) used on
  the game details / library screen.

The tab mirrors the existing Screenshots quick-menu tab in structure and wiring.

## Context

- Quick menu: `app/src/main/java/app/gamenative/ui/component/QuickMenu.kt`, hosted by
  `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` (call site
  ~line 2548).
- Screenshots tab (the pattern we copy):
  `app/src/main/java/app/gamenative/ui/component/ScreenshotsQuickMenuTab.kt`, tab id
  `QuickMenuTab.SCREENSHOTS = 5`, rail button ~QuickMenu.kt:542, pane dispatch
  ~QuickMenu.kt:680, focus dispatch ~QuickMenu.kt:751.
- Achievements modal (what we reuse): `AchievementsDialog` + `AchievementRow` +
  `AchievementDetailDialog` + `HiddenAchievementsSummary` + the `sortedAchievements`
  sort, all currently `private` in
  `app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt`
  (`AchievementsDialog` ~line 1871).
- Data source: `SteamService.fetchAchievementsForDisplay(appId: Int): List<Achievement>?`
  (`SteamService.kt:3221`). Return contract: `null` = fetch failed / not connected /
  timeout; empty list = game genuinely has no achievements; non-empty = has
  achievements. There is no lighter "has achievements" check.
- `Achievement` data class: `app/src/main/java/app/gamenative/ui/data/Achievement.kt`
  (already shared, no move needed).
- Connectivity: `SteamService.keepAlive` keeps the Steam session alive during
  gameplay, so `isConnected` is normally true while a game runs; in explicit offline
  mode the fetch returns `null` and the tab is simply hidden (accepted fallback).

## Components

### 1. Extract the modal into a shared file

Move these out of `LibraryAppScreen.kt` into a new shared file
`app/src/main/java/app/gamenative/ui/component/dialog/AchievementsDialog.kt`:

- `AchievementsDialog(achievements, onDismiss)`
- `AchievementRow`
- `AchievementDetailDialog`
- `HiddenAchievementsSummary`
- the `sortedAchievements` sorting logic (as a helper the callers use)

`LibraryAppScreen` continues to call `AchievementsDialog(...)` exactly as today — no
behavior change on the details screen. The `AchievementsRow` summary card stays in
`LibraryAppScreen` (it is details-screen-specific and not reused here).

Anything the moved composables reference must be public/shared or moved with them.
Verify no leftover reach into other `private` `LibraryAppScreen` helpers.

### 2. New pane composable — `AchievementsQuickMenuTab.kt`

New file `app/src/main/java/app/gamenative/ui/component/AchievementsQuickMenuTab.kt`,
sibling of `ScreenshotsQuickMenuTab.kt`, same layout skeleton (scrollable Column,
`AccentActionRow`, compact rows, `firstItemFocusRequester` on the first focusable):

```
AchievementsQuickMenuTab(
    achievements: List<Achievement>,
    onViewAll: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
)
```

Contents:
- **Header**: `unlocked / total` counter (reuse the count logic used by
  `AchievementsRow`: `unlocked = achievements.count { it.isUnlocked }`, `total =
  achievements.size`).
- **Recent rows**: top N (e.g. 5) achievements as compact rows — small icon
  (`Achievement.icon` / `iconGray` by unlocked state) + `displayName` + locked/unlocked
  indication. Ordering follows the shared `sortedAchievements` helper. First row gets
  `firstItemFocusRequester`.
- **"View all" `AccentActionRow`** → `onViewAll`. Opens the full-screen
  `AchievementsDialog`.

Tapping "View all" is the single entry point into the modal. (Compact rows may also
call `onViewAll`; they do not need a separate per-item dialog path.)

### 3. Quick menu wiring (`QuickMenu.kt`) — mirror Screenshots

- Add `QuickMenuTab.ACHIEVEMENTS = 6` to the `QuickMenuTab` object (~line 117).
- Rail `QuickMenuTabButton` (trophy icon, `Icons.Default.EmojiEvents`,
  `accentColor = PluviaTheme.colors.accentPurple`), rendered **only** inside
  `if (hasAchievements)` — same conditional style as the LSFG tab's
  `if (isLsfgAvailable)`. Selecting sets `selectedTab = QuickMenuTab.ACHIEVEMENTS` and
  persists via `PrefManager.quickMenuLastTab`.
- Tab-title mapping (~line 361): `QuickMenuTab.ACHIEVEMENTS -> R.string.achievements_tab`.
- Pane dispatch (~line 680): `QuickMenuTab.ACHIEVEMENTS -> AchievementsQuickMenuTab(...)`.
- Focus dispatch (~line 751): request focus on the achievements first-item requester.
- New `QuickMenu(...)` params:
  - `achievements: List<Achievement> = emptyList()`
  - `hasAchievements: Boolean = false`
  - `onOpenAchievements: () -> Unit = {}`

Note on last-tab restore: if `PrefManager.quickMenuLastTab` restores to
`ACHIEVEMENTS` but `hasAchievements` is false, fall back to a safe default tab
(e.g. `CONTROLLER`) so the pane is never stuck on a hidden tab.

### 4. `XServerScreen.kt` wiring

- Add state: `var achievements by remember { mutableStateOf<List<Achievement>?>(null) }`
  and `var showAchievementsDialog by remember { mutableStateOf(false) }`.
- `LaunchedEffect(gameId)`: on `Dispatchers.IO`, call
  `SteamService.fetchAchievementsForDisplay(gameId)`, retrying up to 3 times with ~2s
  backoff while the result is `null` (mirror the `BaseAppScreen` retry loop). Store the
  result. `gameId` is already in scope (~XServerScreen.kt:433).
- Derive `hasAchievements = !achievements.isNullOrEmpty()`.
- In the `QuickMenu(...)` call (~2548) pass:
  - `achievements = achievements.orEmpty()`
  - `hasAchievements = hasAchievements`
  - `onOpenAchievements = { showAchievementsDialog = true }`
- Render as a sibling after `QuickMenu(...)`, gated:
  ```
  if (showAchievementsDialog) {
      AchievementsDialog(
          achievements = /* sorted */ achievements.orEmpty(),
          onDismiss = { showAchievementsDialog = false },
      )
  }
  ```
  Unlike the screenshots gallery, this does **not** navigate away or dismiss the quick
  menu — the modal opens over the overlay.

### 5. Strings & icon

- New string resource `achievements_tab` (content-description / tab title). Existing
  `achievements_all_title` etc. already cover the modal.
- Icon: reuse `Icons.Default.EmojiEvents` (Material). No new asset.
- No new data-layer code.

## Data flow

```
XServerScreen mount
  -> LaunchedEffect(gameId): fetchAchievementsForDisplay(gameId)  [IO, retry x3]
       -> achievements state (null | [] | [..])
  -> hasAchievements = !achievements.isNullOrEmpty()
  -> QuickMenu(hasAchievements, achievements, onOpenAchievements)
       -> rail button shown only if hasAchievements
       -> AchievementsQuickMenuTab pane (header + recent + View all)
            -> onViewAll -> showAchievementsDialog = true
  -> AchievementsDialog (shared, identical to details screen)
```

## Error handling / edge cases

- Fetch returns `null` (offline / not connected / timeout after retries) → tab hidden.
- Fetch returns empty list → tab hidden.
- Restored last-tab points at a now-hidden Achievements tab → fall back to default tab.
- Opening the modal does not pause/resume differently from other quick-menu dialogs;
  it renders over the overlay and dismisses back to the menu.

## Testing

- Unit test the gating predicate: `null → hide`, `emptyList → hide`,
  `non-empty → show` (`hasAchievements`).
- Compile check + manual confirmation that the details-screen achievements modal is
  unchanged after the extraction.
- Manual: launch a game with achievements → tab appears → "View all" opens the modal;
  launch a game without achievements → tab absent.

## Out of scope

- Any change to how achievements are fetched, cached, or synced.
- Unlocking achievements from the quick menu.
- Reworking the details-screen `AchievementsRow` summary card.
