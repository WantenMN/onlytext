package org.wanten.onlytext.ui.pages

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wanten.onlytext.ui.components.FileContextMenu
import org.wanten.onlytext.ui.components.FileItem
import org.wanten.onlytext.ui.components.FileListView
import org.wanten.onlytext.ui.state.ProjectState
import org.wanten.onlytext.ui.state.ProjectType
import org.wanten.onlytext.ui.state.fetchChildren

@Composable
fun FileManagerPage(
    project: ProjectState,
    onFileSelected: (FileItem) -> Unit,
    onOpenFolderClick: () -> Unit,
    onCloseFolderClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoadingRoot by remember { mutableStateOf(false) }

    var selectedFileForMenu by remember { mutableStateOf<FileItem?>(null) }

    val allDirectories = remember(project.folderCache.size) {
        val dirs = mutableListOf(FileItem("Root", true, "root", level = 0))
        project.folderCache.values.flatten().filter { it.isDirectory }.forEach {
            if (it.path != "root") dirs.add(it)
        }
        dirs.distinctBy { it.path }
    }

    val currentSiblings = remember(selectedFileForMenu, project.folderCache.size) {
        if (selectedFileForMenu == null) emptyList<FileItem>()
        else {
            val parentPath = project.findParentPath(selectedFileForMenu!!.path) ?: "root"
            project.folderCache[parentPath] ?: emptyList()
        }
    }

    val displayPath = remember(project.path) {
        val path = project.path
        if (path == null) "" else {
            val uri = Uri.parse(path)
            val lastSegment = uri.lastPathSegment ?: ""
            lastSegment.replace("primary:", "")
        }
    }

    LaunchedEffect(project.path, project.type) {
        if (project.path == null || project.type == ProjectType.NONE) return@LaunchedEffect
        if (project.folderCache.containsKey("root")) return@LaunchedEffect

        val uri = Uri.parse(project.path)
        if (project.type == ProjectType.FILE) {
            project.folderCache["root"] = listOf(FileItem(displayPath, false, project.path!!, level = 0))
        } else if (project.type == ProjectType.DIRECTORY) {
            isLoadingRoot = true
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            val rootChildren = fetchChildren(context, uri, rootId, 0)
            project.folderCache["root"] = rootChildren
            isLoadingRoot = false
        }
    }

    val visibleFiles by remember(project) {
        derivedStateOf {
            val list = mutableListOf<FileItem>()
            fun addChildren(parentId: String) {
                project.folderCache[parentId]?.forEach { item ->
                    val isExpanded = project.expandedFolders.contains(item.path)
                    val children = project.folderCache[item.path]
                    val actuallyHasChildren = if (item.isDirectory) {
                        children == null || children.isNotEmpty()
                    } else false
                    
                    list.add(item.copy(isExpanded = isExpanded, hasChildren = actuallyHasChildren))
                    if (item.isDirectory && isExpanded) {
                        addChildren(item.path)
                    }
                }
            }
            addChildren("root")
            list
        }
    }

    LaunchedEffect(visibleFiles, project.isRecursiveExpanding, project.folderCache.size) {
        if (project.path == null || !project.isRecursiveExpanding) return@LaunchedEffect
        val uri = Uri.parse(project.path)

        // Strict DFS: Process the very first folder in visibleFiles that needs expansion or loading.
        // This naturally prioritizes the first child of the first folder, etc.
        for (item in visibleFiles) {
            if (item.isDirectory && !project.skippedFolders.contains(item.path)) {
                if (!item.isExpanded) {
                    // Expand the first encountered unexpanded folder
                    project.expandedFolders = project.expandedFolders + item.path
                    
                    // Immediately fetch its children if not cached
                    if (!project.folderCache.containsKey(item.path)) {
                        val children = fetchChildren(context, uri, item.path, item.level + 1)
                        project.folderCache[item.path] = children
                    }
                    return@LaunchedEffect // Move to next iteration to re-scan from top
                } else if (!project.folderCache.containsKey(item.path)) {
                    // It is expanded but missing cache (could happen during state recovery)
                    val children = fetchChildren(context, uri, item.path, item.level + 1)
                    project.folderCache[item.path] = children
                    return@LaunchedEffect
                }
            }
        }

        // If we traversed the whole visible list and found nothing to expand or fetch, we are done.
        project.isRecursiveExpanding = false
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = contentPadding.calculateTopPadding())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "EXPLORER ${project.projectName}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (project.type != ProjectType.NONE && displayPath.isNotEmpty()) {
                        Text(
                            text = displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
            }

            // List area
            Box(modifier = Modifier.weight(1f)) {
                if (isLoadingRoot) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Loading...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                } else if (visibleFiles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = if (project.type == ProjectType.NONE) "No Project Open" else "Empty Directory", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                } else {
                    key(project.path) {
                        FileListView(
                            files = visibleFiles,
                            scrollState = project.lazyListState,
                            onScrollStateChange = { index, offset ->
                                project.scrollIndex = index
                                project.scrollOffset = offset
                            },
                            onStickyHeaderClick = { file ->
                                // Custom handler for sticky header clicks to prevent jump
                                if (project.isRecursiveExpanding) {
                                    project.skippedFolders = project.skippedFolders + file.path
                                }
                                if (project.expandedFolders.contains(file.path)) {
                                    project.expandedFolders = project.expandedFolders - file.path
                                }
                            },
                            onFileClick = { file ->
                                if (file.isDirectory) {
                                    if (project.isRecursiveExpanding) {
                                        if (project.expandedFolders.contains(file.path)) {
                                            project.skippedFolders = project.skippedFolders + file.path
                                        }
                                    }
                                    
                                    if (project.expandedFolders.contains(file.path)) {
                                        project.expandedFolders = project.expandedFolders - file.path
                                    } else {
                                        project.expandedFolders = project.expandedFolders + file.path
                                        if (!project.folderCache.containsKey(file.path)) {
                                            scope.launch {
                                                val uri = Uri.parse(project.path!!)
                                                val children = fetchChildren(context, uri, file.path, file.level + 1)
                                                project.folderCache[file.path] = children
                                            }
                                        }
                                    }
                                } else {
                                    onFileSelected(file)
                                }
                            },
                            onFileLongClick = { file ->
                                selectedFileForMenu = file
                            }
                        )
                    }
                }
            }

            if (selectedFileForMenu != null) {
                FileContextMenu(
                    file = selectedFileForMenu!!,
                    onDismissRequest = { selectedFileForMenu = null },
                    onRename = { newName ->
                        project.renameFile(context, selectedFileForMenu!!, newName, scope)
                    },
                    onCreateCopy = {
                        project.copyFile(context, selectedFileForMenu!!, scope)
                    },
                    onMove = { targetPath ->
                        project.moveFile(context, selectedFileForMenu!!, targetPath, scope)
                    },
                    onDelete = {
                        project.deleteFile(context, selectedFileForMenu!!, scope)
                    },
                    allDirectories = allDirectories,
                    currentSiblings = currentSiblings
                )
            }

            // Bottom Toolbar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = contentPadding.calculateBottomPadding()),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAnyExpanded = project.expandedFolders.isNotEmpty()
                    val tintColor = MaterialTheme.colorScheme.primary
                    
                    IconButton(onClick = {
                        if (isAnyExpanded) {
                            project.isRecursiveExpanding = false
                            project.skippedFolders = emptySet()
                            project.expandedFolders = emptySet()
                            // Clear all cache except root to truly reset
                            val rootItems = project.folderCache["root"]
                            project.folderCache.clear()
                            if (rootItems != null) project.folderCache["root"] = rootItems
                        } else {
                            project.skippedFolders = emptySet()
                            project.isRecursiveExpanding = true
                        }
                    }) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val sw = 1.2.dp.toPx()
                            val color = tintColor
                            val d = 5.dp.toPx()
                            
                            if (isAnyExpanded) {
                                // Collapse: Inward arrows pointing to each other
                                // Top-right to center
                                drawLine(color, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.55f, size.height * 0.45f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.55f, size.height * 0.45f), Offset(size.width * 0.55f + d, size.height * 0.45f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.55f, size.height * 0.45f), Offset(size.width * 0.55f, size.height * 0.45f - d), sw, StrokeCap.Round)
                                
                                // Bottom-left to center
                                drawLine(color, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.45f, size.height * 0.55f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.45f, size.height * 0.55f), Offset(size.width * 0.45f - d, size.height * 0.55f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.45f, size.height * 0.55f), Offset(size.width * 0.45f, size.height * 0.55f + d), sw, StrokeCap.Round)
                            } else {
                                // Expand: Outward arrows pointing away
                                // Center to top-right
                                drawLine(color, Offset(size.width * 0.55f, size.height * 0.45f), Offset(size.width * 0.8f, size.height * 0.2f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.8f - d, size.height * 0.2f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.2f + d), sw, StrokeCap.Round)
                                
                                // Center to bottom-left
                                drawLine(color, Offset(size.width * 0.45f, size.height * 0.55f), Offset(size.width * 0.2f, size.height * 0.8f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.2f + d, size.height * 0.8f), sw, StrokeCap.Round)
                                drawLine(color, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.2f, size.height * 0.8f - d), sw, StrokeCap.Round)
                            }
                        }
                    }

                    var showMenu by remember { mutableStateOf(false) }
                    
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { showMenu = true }) {
                            Canvas(modifier = Modifier.size(24.dp)) {
                                val radius = 1.8.dp.toPx()
                                val centerX = size.width / 2
                                val centerY = size.height / 2
                                val spacing = 5.dp.toPx()
                                
                                drawCircle(tintColor, radius, Offset(centerX, centerY - spacing))
                                drawCircle(tintColor, radius, Offset(centerX, centerY))
                                drawCircle(tintColor, radius, Offset(centerX, centerY + spacing))
                            }
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            // Refined appearance: subtle background and elevation
                            modifier = Modifier.width(180.dp)
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Open Folder",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    ) 
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenFolderClick()
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            )
                            
                            val isFolderOpen = project.type != ProjectType.NONE
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        "Close Folder",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    ) 
                                },
                                enabled = isFolderOpen,
                                onClick = {
                                    showMenu = false
                                    onCloseFolderClick()
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
