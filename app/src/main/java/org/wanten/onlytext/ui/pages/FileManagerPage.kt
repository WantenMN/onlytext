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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wanten.onlytext.ui.components.FileItem
import org.wanten.onlytext.ui.components.FileListView
import org.wanten.onlytext.ui.state.ProjectState
import org.wanten.onlytext.ui.state.ProjectType

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

    val displayPath = remember(project.path) {
        val path = project.path
        if (path == null) "" else {
            val uri = Uri.parse(path)
            val lastSegment = uri.lastPathSegment ?: ""
            lastSegment.replace("primary:", "")
        }
    }

    // 探测是否为文本文件：基础 MIME 检查 + Binary Sniffing
    fun isTextFile(context: Context, treeUri: Uri, documentId: String, mimeType: String?): Boolean {
        if (mimeType != null) {
            if (mimeType.startsWith("text/")) return true
            if (mimeType == "application/json" || mimeType == "application/xml" || 
                mimeType == "application/javascript" || mimeType.contains("script")) return true
        }

        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            context.contentResolver.openInputStream(docUri)?.use { input ->
                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                if (read <= 0) return true
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) return false // 包含 NULL 字节，判定为二进制
                }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchChildren(treeUri: Uri, documentId: String, level: Int): List<FileItem> = withContext(Dispatchers.IO) {
        val children = mutableListOf<FileItem>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                ),
                null, null, null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    val id = cursor.getString(idIndex)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    
                    if (isDir || isTextFile(context, treeUri, id, mime)) {
                        children.add(FileItem(name, isDir, id, level = level))
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext listOf(FileItem("Error: ${e.message}", false, "error_$documentId", level = level))
        }
        children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
            val rootChildren = fetchChildren(uri, rootId, 0)
            project.folderCache["root"] = rootChildren
            isLoadingRoot = false
        }
    }

    val visibleFiles = remember(project.folderCache.size, project.expandedFolders) {
        val list = mutableListOf<FileItem>()
        fun addChildren(parentId: String) {
            project.folderCache[parentId]?.forEach { item ->
                val isExpanded = project.expandedFolders.contains(item.path)
                list.add(item.copy(isExpanded = isExpanded))
                if (item.isDirectory && isExpanded) {
                    addChildren(item.path)
                }
            }
        }
        addChildren("root")
        list
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
                    FileListView(
                        files = visibleFiles,
                        initialIndex = project.scrollIndex,
                        initialOffset = project.scrollOffset,
                        onScrollStateChange = { index, offset ->
                            project.scrollIndex = index
                            project.scrollOffset = offset
                        },
                        onFileClick = { file ->
                            if (file.isDirectory) {
                                if (project.expandedFolders.contains(file.path)) {
                                    project.expandedFolders = project.expandedFolders - file.path
                                } else {
                                    project.expandedFolders = project.expandedFolders + file.path
                                    if (!project.folderCache.containsKey(file.path)) {
                                        scope.launch {
                                            val uri = Uri.parse(project.path!!)
                                            val children = fetchChildren(uri, file.path, file.level + 1)
                                            project.folderCache[file.path] = children
                                        }
                                    }
                                }
                            } else {
                                onFileSelected(file)
                            }
                        }
                    )
                }
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
                    var showMenu by remember { mutableStateOf(false) }
                    val tintColor = MaterialTheme.colorScheme.primary
                    
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
