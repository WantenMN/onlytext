package org.wanten.onlytext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.wanten.onlytext.ui.pages.EditorPage
import org.wanten.onlytext.ui.pages.SidebarPage
import org.wanten.onlytext.ui.state.MainScreenState
import org.wanten.onlytext.ui.state.rememberMainScreenState
import org.wanten.onlytext.ui.utils.editorPageTransformer
import kotlin.math.abs
import kotlin.math.absoluteValue

@Composable
fun MainScreen() {
    val state = rememberMainScreenState()
    
    val horizontalPageCount = 4
    val verticalPageCount = 2
    
    val hCenterOffset = 500 * horizontalPageCount
    val vCenterOffset = 500 * verticalPageCount
    
    val hPagerState = rememberPagerState(initialPage = hCenterOffset + 1) { hCenterOffset * 2 }
    // "Ghost" vertical pager state for coordinate logic
    val vPagerState = rememberPagerState(initialPage = vCenterOffset) { vCenterOffset * 2 }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(hPagerState, vPagerState) {
        snapshotFlow { hPagerState.currentPage to vPagerState.currentPage }.collect { _ ->
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Infinite logic for Horizontal
    LaunchedEffect(hPagerState) {
        snapshotFlow { hPagerState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                val current = hPagerState.currentPage
                val actual = (current % horizontalPageCount + horizontalPageCount) % horizontalPageCount
                val target = hCenterOffset + actual
                if ((current - target).absoluteValue >= 100) {
                    hPagerState.scrollToPage(target)
                }
            }
        }
    }

    // Infinite logic for Vertical (via Ghost State)
    LaunchedEffect(vPagerState) {
        snapshotFlow { vPagerState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                val current = vPagerState.currentPage
                val actual = (current % verticalPageCount + verticalPageCount) % verticalPageCount
                val target = vCenterOffset + actual
                if ((current - target).absoluteValue >= 100) {
                    vPagerState.scrollToPage(target)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .twoFingerVerticalScroll(vPagerState, scope)
        ) {
            // "Ghost" Pager to handle vertical snapping physics and infinite logic
            // It doesn't render anything, just hosts the state
            VerticalPager(
                state = vPagerState,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0f },
                userScrollEnabled = false
            ) { Box(Modifier.fillMaxSize()) }

            // The main Horizontal Pager
            HorizontalPager(
                state = hPagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) { hPage ->
                val actualCol = (hPage % horizontalPageCount + horizontalPageCount) % horizontalPageCount
                val isSidebar = actualCol == 0 || actualCol == 3

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isSidebar) 1f else 0f)
                        .editorPageTransformer(hPage, hPagerState, actualCol)
                ) {
                    // Manually render Rows based on vPagerState
                    val offsetFraction = vPagerState.currentPageOffsetFraction
                    
                    // We render current and neighbors to support smooth infinite vertical scroll
                    val rowsToRender = if (offsetFraction > 0) {
                        listOf(0 to vPagerState.currentPage, 1 to vPagerState.currentPage + 1)
                    } else if (offsetFraction < 0) {
                        listOf(-1 to vPagerState.currentPage - 1, 0 to vPagerState.currentPage)
                    } else {
                        listOf(0 to vPagerState.currentPage)
                    }

                    rowsToRender.forEach { (offsetIndex, absolutePage) ->
                        val actualRow = (absolutePage % verticalPageCount + verticalPageCount) % verticalPageCount
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = (offsetIndex - offsetFraction) * size.height
                            }
                        ) {
                            RenderPageContent(
                                actualRow = actualRow,
                                actualCol = actualCol,
                                state = state,
                                innerPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = innerPadding.calculateBottomPadding()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderPageContent(
    actualRow: Int,
    actualCol: Int,
    state: MainScreenState,
    innerPadding: PaddingValues
) {
    when (actualCol) {
        0 -> SidebarPage(
            modifier = Modifier.padding(innerPadding),
            title = state.sidebars[actualRow][0].title,
        )
        1 -> {
            val editor = state.editors[actualRow][0]
            EditorPage(
                text = editor.content,
                onTextChange = { editor.content = it },
                innerPadding = innerPadding,
                focusRequester = editor.focusRequester
            )
        }
        2 -> {
            val editor = state.editors[actualRow][1]
            EditorPage(
                text = editor.content,
                onTextChange = { editor.content = it },
                innerPadding = innerPadding,
                focusRequester = editor.focusRequester
            )
        }
        3 -> SidebarPage(
            modifier = Modifier.padding(innerPadding),
            title = state.sidebars[actualRow][1].title,
        )
    }
}

fun Modifier.twoFingerVerticalScroll(
    pagerState: PagerState,
    scope: CoroutineScope
): Modifier = pointerInput(pagerState) {
    awaitPointerEventScope {
        while (true) {
            val startEvent = awaitPointerEvent(PointerEventPass.Initial)
            if (startEvent.changes.size >= 2) {
                var isVerticalIntent: Boolean? = null
                var totalDragX = 0f
                var totalDragY = 0f
                val touchSlop = 10f // Threshold to determine direction

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val changes = event.changes
                    if (changes.all { it.changedToUp() }) break

                    val dragDeltaX = changes.map { it.positionChange().x }.sum()
                    val dragDeltaY = changes.map { it.positionChange().y }.sum()

                    if (isVerticalIntent == null) {
                        totalDragX += abs(dragDeltaX)
                        totalDragY += abs(dragDeltaY)
                        if (totalDragX > touchSlop || totalDragY > touchSlop) {
                            isVerticalIntent = totalDragY > totalDragX
                        }
                    }

                    if (isVerticalIntent == true) {
                        // Locked to vertical: consume all changes to prevent horizontal pager from moving
                        if (changes.size >= 2) {
                            val avgDeltaY = changes.map { it.positionChange().y }.average().toFloat()
                            val sensitivity = 3.0f
                            pagerState.dispatchRawDelta(-avgDeltaY * sensitivity)
                        }
                        changes.forEach { it.consume() }
                    } else if (isVerticalIntent == false) {
                        // Locked to horizontal: let it pass to HorizontalPager
                        // Do not consume, allowing the system to handle the swipe
                    } else {
                        // Determining intent: consume for now to stay still
                        changes.forEach { it.consume() }
                    }
                }

                if (isVerticalIntent == true) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage)
                    }
                }
            }
        }
    }
}
