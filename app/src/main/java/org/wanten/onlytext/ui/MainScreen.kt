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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.util.lerp
import org.wanten.onlytext.ui.components.AppDrawer
import org.wanten.onlytext.ui.components.EditorContent
import kotlin.math.absoluteValue

@Composable
fun MainScreen() {
    // 4 pages: [Sidebar1, Editor1, Editor2, Sidebar2]
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })
    var text1 by remember { mutableStateOf("") }
    var text2 by remember { mutableStateOf("") }
    
    val focusRequester1 = remember { FocusRequester() }
    val focusRequester2 = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Handle focus and keyboard when switching pages
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            when (page) {
                1 -> {
                    focusRequester1.requestFocus()
                    keyboardController?.show()
                }
                2 -> {
                    focusRequester2.requestFocus()
                    keyboardController?.show()
                }
                0, 3 -> {
                    keyboardController?.hide()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Calculate the absolute offset for the current page
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue
                        
                        alpha = lerp(
                            start = 0.6f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                    }
            ) {
                when (page) {
                    0 -> AppDrawer(
                        modifier = Modifier.padding(innerPadding),
                        title = "Sidebar 1",
                        contentAlignment = Alignment.CenterStart
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
                        contentAlignment = Alignment.CenterEnd
                    )
                }
            }
        }
    }
}
