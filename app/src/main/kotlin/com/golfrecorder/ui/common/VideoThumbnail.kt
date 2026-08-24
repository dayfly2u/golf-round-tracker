package com.golfrecorder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun VideoThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    width: Dp = 90.dp,
    height: Dp = 50.dp,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier.size(width = width, height = height)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
