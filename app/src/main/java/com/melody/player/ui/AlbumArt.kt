package com.melody.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.melody.player.R

/**
 * 通用专辑封面组件：有封面时显示封面，无封面/加载失败时显示默认音符占位图，
 * 避免本地歌曲无内嵌封面时出现大片空白。
 */
@Composable
fun AlbumArt(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12)
) {
    val placeholder = painterResource(R.drawable.ic_album_placeholder)
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier = Modifier.fillMaxSize()
        )
    }
}
