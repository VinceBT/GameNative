package app.gamenative.ui.data

import app.gamenative.service.SteamService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Fetch achievements for display, retrying while the result is null. `null` means fetch
 * failed / not connected / timeout; an empty list means the game has no achievements.
 */
suspend fun fetchAchievementsForDisplayRetrying(
    gameId: Int,
    attempts: Int = 3,
    delayMs: Long = 2000,
): List<Achievement>? {
    repeat(attempts) { attempt ->
        val result = try {
            withContext(Dispatchers.IO) {
                SteamService.fetchAchievementsForDisplay(gameId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch achievements for %d", gameId)
            null
        }
        if (result != null) return result
        if (attempt < attempts - 1) delay(delayMs)
    }
    return null
}
