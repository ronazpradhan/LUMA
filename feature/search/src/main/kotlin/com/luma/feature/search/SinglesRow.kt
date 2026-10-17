package com.luma.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luma.data.ytmusic.model.AlbumSummary

/**
 * Horizontal rail of [com.luma.core.ui.components.AlbumSquareCard]s for
 * the Artist Profile "Singles & EPs" shelf.
 *
 * Identical visual to [AlbumsRow] — delegates to it rather than duplicating
 * the LazyRow body so a layout tweak to the albums rail automatically
 * applies to singles too.
 */
@Composable
fun SinglesRow(
    singles: List<AlbumSummary>,
    onClick: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier,
    focusIndex: Int? = null,
) {
    AlbumsRow(
        albums = singles,
        onClick = onClick,
        modifier = modifier,
        focusIndex = focusIndex,
    )
}
