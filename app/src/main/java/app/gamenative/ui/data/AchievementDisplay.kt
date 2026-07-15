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
