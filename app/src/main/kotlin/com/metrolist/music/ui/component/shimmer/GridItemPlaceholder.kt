/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.OnyxRadius
import com.metrolist.music.ui.theme.onyxGlass
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.SmallGridThumbnailHeight
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.utils.rememberEnumPreference

@Composable
fun GridItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth: Boolean = false,
) {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight

    // Same outer padding (8.dp), card radius (24.dp) and inner spacing (12.dp)
    // as the real GridItem, so the shimmer swaps in without a layout jump.
    Column(
        modifier =
        if (fillMaxWidth) {
            modifier
                .padding(8.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(8.dp)
                .width(gridHeight)
        }.onyxGlass(RoundedCornerShape(OnyxRadius), showBorder = false)
            .padding(12.dp),
    ) {
        Spacer(
            modifier =
            if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }.aspectRatio(1f)
                .clip(thumbnailShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextPlaceholder()

        TextPlaceholder()
    }
}
