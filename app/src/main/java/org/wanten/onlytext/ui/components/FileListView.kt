package org.wanten.onlytext.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String, 
    val level: Int = 0,
    val isExpanded: Boolean = false,
    val hasChildren: Boolean = isDirectory,
    val size: Long = 0,
    val lastModified: Long = 0
)

@Composable
fun FileListView(
    files: List<FileItem>,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    scrollState: LazyListState,
    onScrollStateChange: (Int, Int) -> Unit = { _, _ -> },
    onStickyHeaderClick: ((FileItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                onScrollStateChange(index, offset)
            }
    }

    val stickyParents by remember(files, scrollState) {
        derivedStateOf {
            val firstIndex = scrollState.firstVisibleItemIndex
            if (firstIndex < 0 || firstIndex >= files.size) return@derivedStateOf emptyList<FileItem>()
            
            val parents = mutableListOf<FileItem>()
            var currentLevel = files[firstIndex].level
            
            // Find all parent directories of the current top visible item
            for (i in firstIndex - 1 downTo 0) {
                val item = files[i]
                if (item.isDirectory && item.level < currentLevel) {
                    parents.add(0, item)
                    currentLevel = item.level
                }
                if (currentLevel == 0) break
            }
            parents
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(files, key = { it.path + it.level }) { file ->
                FileListItem(
                    file = file,
                    modifier = Modifier
                        .pointerInput(file.path) {
                            detectTapGestures(
                                onTap = { onFileClick(file) },
                                onLongPress = { onFileLongClick(file) }
                            )
                        }
                )
            }
        }

        // Stacked Sticky Headers Overlay
        if (stickyParents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            ) {
                stickyParents.forEach { parent ->
                    FileListItem(
                        file = parent.copy(isExpanded = true),
                        modifier = Modifier.pointerInput(parent.path) {
                            detectTapGestures(
                                onTap = {
                                    if (onStickyHeaderClick != null) {
                                        onStickyHeaderClick(parent)
                                        // Auto-scroll the LazyColumn to this item to avoid visual jump
                                        scope.launch {
                                            val index = files.indexOfFirst { it.path == parent.path }
                                            if (index != -1) {
                                                scrollState.scrollToItem(index)
                                            }
                                        }
                                    } else {
                                        onFileClick(parent)
                                    }
                                }
                            )
                        }
                    )
                }
                // Subtle divider at the bottom of the stack
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                // Draw vertical lines for all parent levels
                for (i in 0 until file.level) {
                    val x = (16 + i * 12 + 6).dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = strokeWidth
                    )
                }
                // If this is an expanded folder, draw the start of the line under the arrow with a gap
                // But only if it actually has children to connect to
                if (file.isExpanded && file.hasChildren) {
                    val x = (16 + file.level * 12 + 6).dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, size.height - 6.dp.toPx()), // Start near the bottom for a clear gap from arrow
                        end = Offset(x, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
            .padding(start = (16 + file.level * 12).dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (file.isDirectory) {
            LineArrow(
                isExpanded = file.isExpanded,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }

        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (file.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1
        )
    }
}

@Composable
private fun LineArrow(isExpanded: Boolean, modifier: Modifier = Modifier) {
    // Distinguish arrow color from text (using primary color)
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    Canvas(modifier = modifier.size(12.dp)) {
        val strokeWidth = 1.2.dp.toPx()
        if (isExpanded) {
            // Down arrow (v)
            drawLine(color, Offset(2.dp.toPx(), 4.dp.toPx()), Offset(6.dp.toPx(), 8.dp.toPx()), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(6.dp.toPx(), 8.dp.toPx()), Offset(10.dp.toPx(), 4.dp.toPx()), strokeWidth, StrokeCap.Round)
        } else {
            // Right arrow (>)
            drawLine(color, Offset(4.dp.toPx(), 2.dp.toPx()), Offset(8.dp.toPx(), 6.dp.toPx()), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(8.dp.toPx(), 6.dp.toPx()), Offset(4.dp.toPx(), 10.dp.toPx()), strokeWidth, StrokeCap.Round)
        }
    }
}
