package com.pokecio.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.ChatLook
import com.pokecio.app.core.DevicePreferences
import com.pokecio.app.core.EncryptionInfo
import com.pokecio.app.core.LocationArea
import com.pokecio.app.core.NotificationText
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.components.rememberNotificationPermission
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

enum class OnboardingStep(@StringRes val title: Int, @StringRes val subtitle: Int) {
    WELCOME(R.string.onboarding_welcome_title, R.string.onboarding_welcome_subtitle),
    UNLOCK(R.string.onboarding_unlock_title, R.string.onboarding_unlock_subtitle),
    CHAT_STYLE(R.string.onboarding_style_title, R.string.onboarding_style_subtitle),
    NOTIFICATIONS(R.string.onboarding_notifications_title, R.string.onboarding_notifications_subtitle),
    LOCATION(R.string.onboarding_location_title, R.string.onboarding_location_subtitle),
    VISIBILITY(R.string.onboarding_visibility_title, R.string.onboarding_visibility_subtitle),
    DONE(R.string.onboarding_done_title, R.string.onboarding_done_subtitle),
}

@Composable
fun OnboardingScreen(
    index: Int,
    onIndexChange: (Int) -> Unit,
    displayName: String?,
    preferences: DevicePreferences,
    encryption: EncryptionInfo?,
    onUseRecoveryKey: () -> Unit,
    onVerifyWithDevice: () -> Unit,
    onChatStyle: (ChatLook) -> Unit,
    onNotificationPreview: (NotificationText) -> Unit,
    onLocationPrecision: (LocationArea) -> Unit,
    onSharePresence: (Boolean) -> Unit,
    onShareTyping: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    val steps = OnboardingStep.entries
    val last = index == steps.lastIndex

    val permission = rememberNotificationPermission()
    val back: () -> Unit = { if (index > 0) onIndexChange(index - 1) }
    val next: () -> Unit = {
        if (steps[index] == OnboardingStep.NOTIFICATIONS && !permission.granted) permission.request()
        if (last) onFinish() else onIndexChange(index + 1)
    }

    BackHandler(enabled = index > 0, onBack = back)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PockyColors.Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(PockyColors.Accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width / 2, 0f),
                        radius = size.width,
                    ),
                )
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            TopBar(
                index = index,
                count = steps.size,
                canGoBack = index > 0,
                onBack = back,
            )

            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally { width -> if (forward) width / 3 else -width / 3 } + fadeIn()
                    val exit = slideOutHorizontally { width -> if (forward) -width / 3 else width / 3 } + fadeOut()
                    enter togetherWith exit
                },
                label = "onboarding",
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 480.dp),
            ) { page ->
                StepPage(steps[page], displayName) {
                    when (steps[page]) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.UNLOCK -> UnlockStep(encryption, onUseRecoveryKey, onVerifyWithDevice)
                        OnboardingStep.CHAT_STYLE -> ChatStyleStep(preferences.chatStyle, onChatStyle)
                        OnboardingStep.NOTIFICATIONS -> NotificationStep(preferences.notificationPreview, onNotificationPreview)
                        OnboardingStep.LOCATION -> LocationStep(preferences.locationPrecision, onLocationPrecision)
                        OnboardingStep.VISIBILITY -> VisibilityStep(
                            sharePresence = preferences.sharePresence,
                            shareTyping = preferences.shareTyping,
                            onSharePresence = onSharePresence,
                            onShareTyping = onShareTyping,
                        )
                        OnboardingStep.DONE -> DoneStep(preferences, encryption) { onIndexChange(it.ordinal) }
                    }
                }
            }

            Column(modifier = Modifier.widthIn(max = 480.dp)) {
                GradientButton(
                    text = stringResource(if (last) R.string.onboarding_finish else R.string.onboarding_next),
                    onClick = next,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TopBar(index: Int, count: Int, canGoBack: Boolean, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        IconButton(
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier.alpha(if (canGoBack) 1f else 0f),
        ) {
            Icon(
                imageVector = PockyIcons.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = PockyColors.Ink,
            )
        }
        Spacer(Modifier.width(8.dp))
        StepBar(index, count, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.onboarding_progress, index + 1, count),
            style = MaterialTheme.typography.labelLarge,
            color = PockyColors.InkMuted,
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun StepBar(index: Int, count: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        repeat(count) { position ->
            val fill by animateFloatAsState(if (position <= index) 1f else 0f, label = "step")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(PockyColors.InkMuted.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(PockyGradient),
                )
            }
        }
    }
}

@Composable
private fun StepPage(
    step: OnboardingStep,
    displayName: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, bottom = 24.dp),
    ) {
        Text(
            text = when {
                step != OnboardingStep.WELCOME -> stringResource(step.title)
                displayName.isNullOrBlank() -> stringResource(R.string.onboarding_welcome_title_anonymous)
                else -> stringResource(step.title, displayName)
            },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = PockyColors.Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(step.subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = PockyColors.InkMuted,
        )
        Spacer(Modifier.height(28.dp))
        content()
    }
}
