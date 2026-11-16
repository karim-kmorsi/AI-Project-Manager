package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

/** Shared by display text and inputs; does not transform the value or reset selection. */
@Composable
internal fun contentTextStyle(base: TextStyle = LocalTextStyle.current): TextStyle =
    base.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Start)

/**
 * Block text uses the available width and each paragraph's natural reading direction.
 * Arabic starts on the right and English on the left, even in mixed-language projects.
 * Neutral-only paragraphs fall back to Android's locale layout direction. Inline labels
 * and Material controls keep their intrinsic sizing and inherited start/end ordering.
 */
@Composable
internal fun ParagraphText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    fillWidth: Boolean = true
) {
    Text(text, modifier = if (fillWidth) modifier.fillMaxWidth() else modifier, color = color, fontWeight = fontWeight,
        style = contentTextStyle(style), maxLines = maxLines)
}
