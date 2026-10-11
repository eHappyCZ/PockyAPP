package com.pokecio.app.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.components.PockyGradient
import com.pokecio.app.ui.layout.LocalScreenSize
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.components.comingSoon
import com.pokecio.app.ui.theme.PockyIcons

enum class HomeTab(@DrawableRes val icon: Int, @StringRes val label: Int, val enabled: Boolean) {
    CHATS(R.drawable.pi_chats, R.string.tab_chats, true),
    TIMELINE(R.drawable.pi_timeline, R.string.tab_timeline, false),
    NEWS(R.drawable.pi_news, R.string.tab_news, false),
    MAP(R.drawable.pi_map, R.string.tab_map, true),
    ME(R.drawable.pi_user, R.string.tab_me, true),
}

private val BarHeight = 64.dp
private val RailWidth = 88.dp

@Composable
fun HomeScreen(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    onCompose: () -> Unit,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = BarHeight + bottomInset

    if (LocalScreenSize.current.twoPane) {
        Row(
            Modifier
                .fillMaxSize()
                .background(PockyColors.Bg),
        ) {
            SideRail(selected = tab, onTab = onTab, onCompose = onCompose)
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
                content(bottomInset)
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        content(bottomPadding)

        if (tab == HomeTab.CHATS) {
            ComposeButton(
                onClick = onCompose,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = bottomPadding + 16.dp),
            )
        }

        BottomBar(
            selected = tab,
            onTab = onTab,
            bottomInset = bottomInset,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ComposeButton(onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = PockyColors.Accent, ambientColor = PockyColors.Accent)
            .size(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PockyGradient)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = PockyIcons.Compose,
            contentDescription = stringResource(R.string.rooms_new_chat),
            tint = PockyColors.Ink,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SideRail(selected: HomeTab, onTab: (HomeTab) -> Unit, onCompose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
    ) {
        ComposeButton(onClick = onCompose, modifier = Modifier)
        Spacer(Modifier.height(18.dp))
        HomeTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = tab == selected,
                onClick = { onTab(tab) },
                modifier = Modifier.width(RailWidth - 12.dp),
            )
        }
    }
}

@Composable
private fun BottomBar(selected: HomeTab, onTab: (HomeTab) -> Unit, bottomInset: Dp, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(PockyColors.Bg.copy(alpha = 0.97f)),
    ) {
        HorizontalDivider(color = PockyColors.InkMuted.copy(alpha = 0.1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight),
        ) {
            HomeTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onTab(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

@Composable
private fun TabItem(tab: HomeTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val pill by animateColorAsState(
        if (selected) PockyColors.Accent.copy(alpha = 0.22f) else PockyColors.Bg.copy(alpha = 0f),
        label = "tab",
    )
    val tint = if (selected) PockyColors.Ink else PockyColors.InkMuted
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(if (tab.enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !selected, role = Role.Tab) {
                if (tab.enabled) onClick() else comingSoon(context)
            }
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(pill, CircleShape),
        ) {
            Icon(ImageVector.vectorResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
