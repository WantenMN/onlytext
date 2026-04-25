package org.wanten.onlytext.ui.utils

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

fun Modifier.editorPageTransformer(
    page: Int,
    pagerState: PagerState,
    actualPage: Int
): Modifier = this.graphicsLayer {
    val pagePosition = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)
    
    // Logic for Editor dimming and translation
    if (actualPage == 1 || actualPage == 2) {
        if (actualPage == 1 && pagePosition > 0 && pagePosition <= 1) {
            translationX = -pagePosition * size.width
        } else if (actualPage == 2 && pagePosition < 0 && pagePosition >= -1) {
            translationX = -pagePosition * size.width
        }
        
        val dimAmount = if (actualPage == 1) {
            pagePosition.coerceIn(0f, 1f)
        } else {
            (-pagePosition).coerceIn(0f, 1f)
        }
        alpha = lerp(1f, 0.5f, dimAmount)
    }
}
