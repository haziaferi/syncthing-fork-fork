package com.nutomic.syncthingandroid.theme

import androidx.compose.runtime.Composable

/** [allowWallpaper] = false keeps the brand palette for Compose pieces hosted inside XML screens. */
@Composable
fun ApplicationTheme(
    allowWallpaper: Boolean = true,
    content: @Composable () -> Unit
) = ForkTheme(allowWallpaper, content)
