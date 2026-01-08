package com.example.stocksignal.ui.components

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSizeSp = with(LocalDensity.current) {
        if (style.fontSize == TextUnit.Unspecified) 14f else style.fontSize.value
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                textSize = textSizeSp
                setLineSpacing(0f, 1.1f)
                includeFontPadding = false
                this.maxLines = maxLines
                ellipsize = when (overflow) {
                    TextOverflow.Ellipsis -> TextUtils.TruncateAt.END
                    TextOverflow.Clip -> null
                    else -> TextUtils.TruncateAt.END
                }
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.textSize = textSizeSp
            view.maxLines = maxLines
            view.ellipsize = when (overflow) {
                TextOverflow.Ellipsis -> TextUtils.TruncateAt.END
                TextOverflow.Clip -> null
                else -> TextUtils.TruncateAt.END
            }
            view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    )
}
