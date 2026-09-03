package app.gamenative.ui.screen

import android.net.Uri

/**
 * Destinations for top level screens, excluding home screen destinations.
 */
sealed class PluviaScreen(val route: String) {
    data object LoginUser : PluviaScreen("login")
    data object Home : PluviaScreen("home")
    data object XServer : PluviaScreen("xserver")
    data object Settings : PluviaScreen("settings")
    data object Chat : PluviaScreen("chat/{id}") {
        fun route(id: Long) = "chat/$id"
        const val ARG_ID = "id"
    }
    data object Achievements : PluviaScreen("achievements/{appId}") {
        fun route(appId: String) = "achievements/${Uri.encode(appId)}"
        const val ARG_APP_ID = "appId"
    }
}
