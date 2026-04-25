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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import org.wanten.onlytext.ui.pages.EditorPage
import org.wanten.onlytext.ui.pages.SidebarPage
import org.wanten.onlytext.ui.state.rememberMainScreenState
import org.wanten.onlytext.ui.utils.editorPageTransformer
import kotlin.math.absoluteValue

@Composable
fun MainScreen() {
    val state = rememberMainScreenState()
    
    val actualPageCount = 4
    val centerOffset = 500 * actualPageCount
    val pagerState = rememberPagerState(initialPage = centerOffset + 1) { centerOffset * 2 }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Hide keyboard and clear focus on page change
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { _ ->
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Infinite scroll logic
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isSidebar) 1f else 0f)
                    .editorPageTransformer(page, pagerState, actualPage)
            ) {
                when (actualPage) {
                    0 -> SidebarPage(
                        modifier = Modifier.padding(innerPadding),
                        title = "Left Sidebar",
                    )
                    1 -> EditorPage(
                        text = state.leftNoteContent,
                        onTextChange = { state.leftNoteContent = it },
                        innerPadding = innerPadding,
                        focusRequester = state.leftFocusRequester
                    )
                    2 -> EditorPage(
                        text = state.rightNoteContent,
                        onTextChange = { state.rightNoteContent = it },
                        innerPadding = innerPadding,
                        focusRequester = state.rightFocusRequester
                    )
                    3 -> SidebarPage(
                        modifier = Modifier.padding(innerPadding),
                        title = "Right Sidebar",
                    )
                }
            }
        }
    }
}
