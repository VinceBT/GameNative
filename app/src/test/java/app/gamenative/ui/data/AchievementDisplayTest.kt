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
