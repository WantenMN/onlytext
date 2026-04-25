package org.wanten.onlytext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import org.wanten.onlytext.ui.components.AppDrawer
import org.wanten.onlytext.ui.components.EditorContent
import kotlin.math.absoluteValue

@Composable
fun MainScreen() {
    val actualPageCount = 4
    val centerOffset = 500 * actualPageCount
    val pagerState = rememberPagerState(initialPage = centerOffset + 1) { centerOffset * 2 }
    
    var text1 by remember { mutableStateOf("") }
    var text2 by remember { mutableStateOf("") }
    
    val focusRequester1 = remember { FocusRequester() }
    val focusRequester2 = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { _ ->
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                val current = pagerState.currentPage
                val actual = (current % actualPageCount + actualPageCount) % actualPageCount
                val target = centerOffset + actual
                if ((current - target).absoluteValue >= 100) {
                    pagerState.scrollToPage(target)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { page ->
            val actualPage = (page % actualPageCount + actualPageCount) % actualPageCount
            val isSidebar = actualPage == 0 || actualPage == 3
            val isEditor = actualPage == 1 || actualPage == 2

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isSidebar) 1f else 0f)
                    .graphicsLayer {
                        val pagePosition = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        
                        if (isEditor) {
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
            ) {
                when (actualPage) {
                    0 -> AppDrawer(
                        modifier = Modifier.padding(innerPadding),
                        title = "Sidebar 1",
                    )
                    1 -> EditorContent(
                        text = text1,
                        onTextChange = { text1 = it },
                        innerPadding = innerPadding,
                        focusRequester = focusRequester1
                    )
                    2 -> EditorContent(
                        text = text2,
                        onTextChange = { text2 = it },
                        innerPadding = innerPadding,
                        focusRequester = focusRequester2
                    )
                    3 -> AppDrawer(
                        modifier = Modifier.padding(innerPadding),
                        title = "Sidebar 2",
                    )
                }
            }
        }
    }
}
