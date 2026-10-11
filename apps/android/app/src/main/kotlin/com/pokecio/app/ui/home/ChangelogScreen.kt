package com.pokecio.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.ReleaseNotes
import com.pokecio.app.ui.people.PeopleHeader
import com.pokecio.app.ui.rooms.AppLocale
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ChangelogScreen(
    releases: List<ReleaseNotes>,
    installedCode: UInt,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PockyColors.Bg),
    ) {
        PeopleHeader(stringResource(R.string.changelog_title), null, false, onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            itemsIndexed(releases, key = { _, release -> release.versionCode.toInt() }) { index, release ->
                ReleaseCard(release, installed = release.versionCode == installedCode, latest = index == 0)
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ReleaseNotes, installed: Boolean, latest: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PockyColors.Surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.version,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Spacer(Modifier.width(10.dp))
            Badge(release.channel, PockyColors.AccentSoft)
            if (installed) {
                Spacer(Modifier.width(6.dp))
                Badge(stringResource(R.string.changelog_installed), PockyColors.Success)
            }
        }
        if (latest) {
            Text(
                text = releaseDate(release.date),
                style = MaterialTheme.typography.bodySmall,
                color = PockyColors.InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (release.added.isNotEmpty()) {
            Group(
                title = stringResource(R.string.changelog_added),
                icon = PockyIcons.Sparkle,
                tint = PockyColors.AccentSoft,
                items = release.added,
            )
        }
        if (release.fixed.isNotEmpty()) {
            Group(
                title = stringResource(R.string.changelog_fixed),
                icon = PockyIcons.CheckCircle,
                tint = PockyColors.Success,
                items = release.fixed,
            )
        }
    }
}

@Composable
private fun Group(title: String, icon: ImageVector, tint: Color, items: List<String>) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
    Spacer(Modifier.height(6.dp))
    items.forEach { item ->
        Row(Modifier.padding(start = 4.dp, top = 4.dp)) {
            Text("•", color = tint, modifier = Modifier.padding(end = 10.dp))
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                color = PockyColors.Ink,
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun releaseDate(date: String): String =
    runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(AppLocale))
    }.getOrDefault(date)
