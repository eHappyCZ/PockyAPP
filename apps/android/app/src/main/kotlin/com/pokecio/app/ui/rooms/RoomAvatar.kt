package com.pokecio.app.ui.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokecio.app.media.MatrixImage
import com.pokecio.app.media.rememberMatrixImage

private val avatarColors = listOf(
    Color(0xFF7C3AED),
    Color(0xFF8B5CF6),
    Color(0xFF6366F1),
    Color(0xFFDB2777),
    Color(0xFF0EA5E9),
    Color(0xFF059669),
    Color(0xFFD97706),
)

@Composable
fun RoomAvatar(
    id: String,
    name: String,
    url: String? = null,
    size: Dp = 48.dp,
    rounded: Boolean = false,
) {
    val shape = if (rounded) RoundedCornerShape(size * ROUNDED_CORNER) else CircleShape
    val color = avatarColors[Math.floorMod(id.hashCode(), avatarColors.size)]
    val image = rememberMatrixImage(url, size, size)

    if (image != null) {
        MatrixImage(
            image = image,
            contentDescription = name,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
        return
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(color, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.34f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

const val ROUNDED_CORNER = 0.3f

private fun initials(name: String): String {
    val words = name.trimStart('#', '@', '!', ' ')
        .split(' ', '.', '_', '-', '·')
        .filter { it.isNotBlank() }
    val letters = when {
        words.isEmpty() -> return "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }
    return letters.uppercase()
}
