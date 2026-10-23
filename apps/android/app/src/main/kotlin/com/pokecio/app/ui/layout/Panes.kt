package com.pokecio.app.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

private val ListPaneWidth = 380.dp
private val NarrowListPaneWidth = 320.dp

@Composable
fun ChatsLayout(
    twoPane: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    if (!twoPane) {
        list()
        return
    }
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(if (LocalScreenSize.current == ScreenSize.MEDIUM) NarrowListPaneWidth else ListPaneWidth)
                .fillMaxHeight(),
        ) {
            list()
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(PockyColors.InkMuted.copy(alpha = 0.1f)),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            detail()
        }
    }
}

@Composable
fun EmptyChatPane() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(PockyGradient, CircleShape),
            ) {
                Icon(PockyIcons.Chats, contentDescription = null, tint = PockyColors.OnBrand, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.pane_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PockyColors.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.pane_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

val SidePaneWidth = 400.dp

@Composable
fun SidePanel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(260), expandFrom = Alignment.Start) + fadeIn(tween(260)),
        exit = shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start) + fadeOut(tween(220)),
    ) {
        Row(Modifier.fillMaxHeight()) {
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(PockyColors.InkMuted.copy(alpha = 0.1f)),
            )
            Box(
                Modifier
                    .width(SidePaneWidth)
                    .fillMaxHeight(),
            ) {
                content()
            }
        }
    }
}

val ReadableWidth = 680.dp

@Composable
fun Readable(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled || !LocalScreenSize.current.twoPane) {
        content()
        return
    }
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        Box(
            Modifier
                .widthIn(max = ReadableWidth)
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}
