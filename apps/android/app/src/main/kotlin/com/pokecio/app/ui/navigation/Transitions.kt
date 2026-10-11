package com.pokecio.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

private const val SLIDE_MS = 300
private const val FADE_MS = 220

enum class Screen(val depth: Int, val modal: Boolean = false, val sensitive: Boolean = false) {
    BLANK(0, modal = true),
    ONBOARDING(0, modal = true),
    VERIFICATION(9, modal = true, sensitive = true),
    RECOVERY(9, modal = true, sensitive = true),
    SETUP_BACKUP(9, modal = true, sensitive = true),
    RESET_IDENTITY(9, modal = true, sensitive = true),
    HOME(0),
    ROOM(1),
    DEVICES(2),
    CHANGELOG(2),
    PASSPHRASE(2, sensitive = true),
    PASSWORD(2, sensitive = true),
    APP_LOCK(2, sensitive = true),
    DELETE_ACCOUNT(2, sensitive = true),
    NEW_CHAT(2),
    NEW_GROUP(3),
    INVITE(3),
}

fun AnimatedContentTransitionScope<Screen>.screenTransition(): ContentTransform {
    if (initialState.modal || targetState.modal) {
        return fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS))
    }
    val forward = targetState.depth >= initialState.depth
    val slide = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
    val fade = tween<Float>(SLIDE_MS)
    val transform = if (forward) {
        (slideInHorizontally(slide) { it } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade))
    } else {
        (slideInHorizontally(slide) { -it / 4 } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { it } + fadeOut(fade))
    }
    transform.targetContentZIndex = if (forward) 1f else -1f
    return transform
}

@Composable
fun PushedLayer(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(SLIDE_MS)),
        exit = slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(SLIDE_MS)),
    ) {
        content()
    }
}

@Composable
fun RaisedLayer(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(FADE_MS)),
        exit = slideOutVertically(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(FADE_MS)),
    ) {
        content()
    }
}
