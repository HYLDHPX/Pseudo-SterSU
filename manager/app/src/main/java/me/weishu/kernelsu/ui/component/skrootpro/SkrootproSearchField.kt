package me.weishu.kernelsu.ui.component.skrootpro

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R

@Composable
fun SkrootproSearchField(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    @StringRes placeholderRes: Int = R.string.superuser_search_hint,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(SkrootproColors.SurfaceStrong)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = SkrootproColors.Muted,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            singleLine = true,
            textStyle = TextStyle(
                color = SkrootproColors.Text,
                fontSize = skrootproSp(15f, maxScale = 1.05f),
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(SkrootproColors.Purple),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (searchText.isBlank()) {
                        Text(
                            text = stringResource(placeholderRes),
                            color = SkrootproColors.Muted,
                            fontSize = skrootproSp(15f, maxScale = 1.05f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (searchText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onClearSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = SkrootproColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
