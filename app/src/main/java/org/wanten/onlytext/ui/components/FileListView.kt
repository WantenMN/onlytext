package org.wanten.onlytext.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String, 
    val level: Int = 0,
    val isExpanded: Boolean = false,
    val hasChildren: Boolean = isDirectory
)

@Composable
fun FileListView(
    files: List<FileItem>,
    onFileClick: (FileItem) -> Unit,
    initialIndex: Int = 0,
    initialOffset: Int = 0,
    onScrollStateChange: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
        initialFirstVisibleItemScrollOffset = initialOffset
    )

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                onScrollStateChange(index, offset)
            }
    }

    LazyColumn(
        state = scrollState,
        modifier = modifier.fillMaxSize()
    ) {
        items(files, key = { it.path + it.level }) { file ->
            FileListItem(
                file = file,
                onClick = { onFileClick(file) }
            )
        }
    }
}

@Composable
fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
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
                if (file.isExpanded) {
                    val x = (16 + file.level * 12 + 6).dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, size.height - 6.dp.toPx()), // Start near the bottom for a clear gap from arrow
                        end = Offset(x, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
            .clickable(onClick = onClick)
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
