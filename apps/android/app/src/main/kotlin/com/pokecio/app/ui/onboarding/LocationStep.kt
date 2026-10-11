package com.pokecio.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.core.LocationArea
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.theme.PockyColors

@Composable
fun LocationStep(selected: LocationArea, onSelect: (LocationArea) -> Unit) {
    MapPreview(selected)
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            title = stringResource(R.string.onboarding_location_exact),
            body = stringResource(R.string.onboarding_location_exact_body),
            selected = selected == LocationArea.EXACT,
            onClick = { onSelect(LocationArea.EXACT) },
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_location_neighborhood),
            body = stringResource(R.string.onboarding_location_neighborhood_body),
            selected = selected == LocationArea.NEIGHBORHOOD,
            onClick = { onSelect(LocationArea.NEIGHBORHOOD) },
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_location_city),
            body = stringResource(R.string.onboarding_location_city_body),
            badge = stringResource(R.string.onboarding_recommended),
            selected = selected == LocationArea.CITY,
            onClick = { onSelect(LocationArea.CITY) },
        )
    }
}

@Composable
private fun MapPreview(area: LocationArea) {
    val radius by animateDpAsState(
        targetValue = when (area) {
            LocationArea.EXACT -> 10.dp
            LocationArea.NEIGHBORHOOD -> 44.dp
            LocationArea.CITY -> 120.dp
        },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "radius",
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(ControlShape)
            .border(1.dp, PockyColors.InkMuted.copy(alpha = 0.12f), ControlShape),
    ) {
        drawRect(PockyColors.Surface)
        drawCity()

        val center = Offset(size.width * 0.52f, size.height * 0.5f)
        val r = radius.toPx()
        drawCircle(PockyColors.Accent.copy(alpha = 0.18f), r, center)
        drawCircle(PockyColors.AccentSoft.copy(alpha = 0.7f), r, center, style = Stroke(1.5.dp.toPx()))

        if (area == LocationArea.EXACT) {
            drawCircle(PockyColors.AccentSoft.copy(alpha = 0.5f * (1f - pulse)), r + 18.dp.toPx() * pulse, center)
            drawCircle(PockyColors.Ink, 6.dp.toPx(), center)
            drawCircle(PockyColors.Accent, 4.dp.toPx(), center)
        }
    }
}

private fun DrawScope.drawCity() {
    val w = size.width
    val h = size.height
    val park = PockyColors.Success.copy(alpha = 0.08f)
    drawRect(park, Offset(w * 0.08f, h * 0.12f), Size(w * 0.18f, h * 0.28f))
    drawRect(park, Offset(w * 0.70f, h * 0.62f), Size(w * 0.22f, h * 0.24f))

    val river = Path().apply {
        moveTo(0f, h * 0.78f)
        cubicTo(w * 0.3f, h * 0.62f, w * 0.55f, h * 0.98f, w, h * 0.70f)
    }
    drawPath(river, PockyColors.GradientStart.copy(alpha = 0.35f), style = Stroke(14.dp.toPx()))

    val street = PockyColors.InkMuted.copy(alpha = 0.14f)
    val main = PockyColors.InkMuted.copy(alpha = 0.22f)
    listOf(0.18f, 0.36f, 0.58f, 0.83f).forEach { x ->
        drawLine(street, Offset(w * x, 0f), Offset(w * (x + 0.04f), h), 2.dp.toPx())
    }
    listOf(0.22f, 0.45f, 0.66f).forEach { y ->
        drawLine(street, Offset(0f, h * y), Offset(w, h * (y - 0.05f)), 2.dp.toPx())
    }
    drawLine(main, Offset(0f, h * 0.05f), Offset(w, h * 0.95f), 4.dp.toPx())
}
