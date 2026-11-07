package com.stepvault.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val fittedSize = remember(
            text.length,
            maxWidthPx,
            style,
            maxFontSize,
            minFontSize,
            maxLines,
            density,
            layoutDirection,
        ) {
            if (maxWidthPx == Constraints.Infinity) {
                maxFontSize.value
            } else {
                var candidate = maxFontSize.value
                val lowerBound = minFontSize.value
                while (candidate > lowerBound) {
                    val result = measurer.measure(
                        text = AnnotatedString(text),
                        style = style.copy(fontSize = candidate.sp),
                        maxLines = maxLines,
                    )
                    if (result.size.width <= maxWidthPx) break
                    candidate -= 1f
                }
                candidate
            }
        }

        Text(
            text = text,
            style = style.copy(fontSize = fittedSize.sp),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
        )
    }
}
