package org.wanten.onlytext.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileContextMenu(
    file: FileItem,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onCreateCopy: () -> Unit,
    onMove: (String) -> Unit, // Path to move to
    onDelete: () -> Unit,
    onCreateNew: (String, Boolean) -> Unit, // Name, isDirectory
    allDirectories: List<FileItem> = emptyList(), // For move operation
    currentSiblings: List<FileItem> = emptyList(), // For rename validation
    projectRootPath: String? = null,
    actualParentPath: String? = null
) {
    var currentMenu by remember { mutableStateOf(MenuState.MAIN) }
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var targetMovePath by remember { mutableStateOf("") }
    var targetMoveName by remember { mutableStateOf("") }
    var isCreatingDirectory by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { if (currentMenu == MenuState.MAIN) BottomSheetDefaults.DragHandle() }
    ) {
        when (currentMenu) {
            MenuState.MAIN -> MainMenu(
                file = file,
                onRenameClick = { currentMenu = MenuState.RENAME },
                onCreateCopyClick = {
                    onCreateCopy()
                    onDismissRequest()
                },
                onMoveClick = { currentMenu = MenuState.MOVE },
                onDeleteClick = { currentMenu = MenuState.DELETE },
                onCreateFileClick = {
                    isCreatingDirectory = false
                    currentMenu = MenuState.CREATE
                },
                onCreateFolderClick = {
                    isCreatingDirectory = true
                    currentMenu = MenuState.CREATE
                }
            )
            MenuState.RENAME -> RenameMenu(
                file = file,
                siblings = currentSiblings,
                onConfirm = { newName ->
                    onRename(newName)
                    onDismissRequest()
                },
                onBack = { currentMenu = MenuState.MAIN }
            )
            MenuState.MOVE -> MoveMenu(
                file = file,
                directories = allDirectories,
                projectRootPath = projectRootPath,
                onConfirm = { targetPath, targetName ->
                    targetMovePath = targetPath
                    targetMoveName = targetName
                    currentMenu = MenuState.MOVE_CONFIRM
                },
                onBack = { currentMenu = MenuState.MAIN }
            )
            MenuState.MOVE_CONFIRM -> MoveConfirmMenu(
                fileName = file.name,
                targetName = targetMoveName,
                onConfirm = {
                    onMove(targetMovePath)
                    onDismissRequest()
                },
                onBack = { currentMenu = MenuState.MOVE }
            )
            MenuState.CREATE -> CreateItemMenu(
                isDirectory = isCreatingDirectory,
                targetPath = actualParentPath ?: (projectRootPath ?: "root"),
                projectRootPath = projectRootPath,
                onConfirm = { name ->
                    onCreateNew(name, isCreatingDirectory)
                    onDismissRequest()
                },
                onBack = { currentMenu = MenuState.MAIN }
            )
            MenuState.DELETE -> DeleteConfirmMenu(
                file = file,
                onConfirm = {
                    onDelete()
                    onDismissRequest()
                },
                onBack = { currentMenu = MenuState.MAIN }
            )
            MenuState.CONFLICT -> ConflictMenu(
                fileName = file.name,
                onReplace = {
                    onMove(targetMovePath)
                    onDismissRequest()
                },
                onAddSuffix = {
                    onMove(targetMovePath)
                    onDismissRequest()
                },
                onCancel = { currentMenu = MenuState.MOVE }
            )
        }
    }
}

enum class MenuState {
    MAIN, RENAME, MOVE, MOVE_CONFIRM, DELETE, CONFLICT, CREATE
}

@Composable
private fun MainMenu(
    file: FileItem,
    onRenameClick: () -> Unit,
    onCreateCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCreateFileClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Meta Info Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (file.isDirectory) "Folder" else "File",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (!file.isDirectory) {
                    Text(
                        text = " • ${formatSize(file.size)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (file.lastModified > 0) {
                Text(
                    text = "Last Modified: ${formatDate(file.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        MenuItem(icon = Icons.Default.Add, text = "New File", onClick = onCreateFileClick)
        MenuItem(icon = Icons.Default.CreateNewFolder, text = "New Folder", onClick = onCreateFolderClick)
        MenuItem(icon = Icons.Default.Edit, text = "Rename", onClick = onRenameClick)
        MenuItem(icon = Icons.Default.ContentCopy, text = "Create Copy", onClick = onCreateCopyClick)
        MenuItem(icon = Icons.AutoMirrored.Filled.DriveFileMove, text = "Move to...", onClick = onMoveClick)
        MenuItem(icon = Icons.Default.Delete, text = "Delete", onClick = onDeleteClick, isDestructive = true)
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RenameMenu(
    file: FileItem,
    siblings: List<FileItem>,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit
) {
    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(file.name, TextRange(file.name.length))) 
    }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    val newName = textFieldValue.text
    val isAlreadyExists = remember(newName) {
        siblings.any { it.name == newName && it.path != file.path }
    }
    val isIllegal = remember(newName) {
        newName.isBlank() || newName.contains("/") || newName.contains("\\")
    }
    val isValid = !isAlreadyExists && !isIllegal && newName != file.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Rename",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text("New Name") },
            singleLine = true,
            isError = isAlreadyExists || (isIllegal && newName.isNotBlank()),
            supportingText = {
                if (isAlreadyExists) {
                    Text("A file or folder with this name already exists")
                } else if (isIllegal && newName.isNotBlank()) {
                    Text("Invalid characters in name")
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onConfirm(newName) },
                enabled = isValid
            ) {
                Text("Rename")
            }
        }
    }
}

@Composable
private fun MoveMenu(
    file: FileItem,
    directories: List<FileItem>,
    projectRootPath: String?,
    onConfirm: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredDirectories = remember(searchQuery, directories) {
        directories.filter { 
            val isNotSelfOrChild = it.path != file.path && !it.path.startsWith(file.path + "/")
            if (!isNotSelfOrChild) return@filter false
            
            if (searchQuery.isEmpty()) return@filter true
            
            val name = it.name
            val path = simplifyPath(it.path, projectRootPath)
            
            fuzzyMatch(searchQuery, name) || fuzzyMatch(searchQuery, path)
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(screenHeight * 0.8f) // Fixed height ratio
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Move to",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(filteredDirectories) { dir ->
                val displayPath = simplifyPath(dir.path, projectRootPath)
                
                ListItem(
                    headlineContent = { Text(dir.name) },
                    supportingContent = { 
                        Text(displayPath, maxLines = 1) 
                    },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onConfirm(dir.path, dir.name) }
                )
            }
        }

        // Search bar with better margins and rounded corners
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Clear margins
            placeholder = { Text("Search folders...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun MoveConfirmMenu(
    fileName: String,
    targetName: String,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Confirm Move",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Move \"$fileName\" to \"$targetName\"?",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onConfirm) {
                Text("Move")
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun simplifyPath(path: String, rootPath: String?): String {
    var p = path.replace("primary:", "")
    if (rootPath != null) {
        val rootP = rootPath.replace("primary:", "")
        if (p.startsWith(rootP)) {
            p = p.substring(rootP.length)
            if (p.startsWith("/")) p = p.substring(1)
            if (p.isEmpty()) return "Project Root"
        }
    }
    return p
}

private fun fuzzyMatch(query: String, target: String): Boolean {
    if (query.isEmpty()) return true
    if (target.isEmpty()) return false
    
    var queryIdx = 0
    var targetIdx = 0
    
    val lowerQuery = query.lowercase()
    val lowerTarget = target.lowercase()
    
    while (queryIdx < lowerQuery.length && targetIdx < lowerTarget.length) {
        if (lowerQuery[queryIdx] == lowerTarget[targetIdx]) {
            queryIdx++
        }
        targetIdx++
    }
    
    return queryIdx == lowerQuery.length
}

@Composable
private fun CreateItemMenu(
    isDirectory: Boolean,
    targetPath: String,
    projectRootPath: String?,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit
) {
    // Initial name is Untitled.txt, but user can delete extension as requested
    val initialName = if (isDirectory) "Untitled" else "Untitled.txt"
    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length))) 
    }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val displayPath = simplifyPath(targetPath, projectRootPath)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = if (isDirectory) "New Folder" else "New File",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Target: $displayPath",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text("Name") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onConfirm(textFieldValue.text) },
                enabled = textFieldValue.text.isNotBlank()
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
private fun DeleteConfirmMenu(
    file: FileItem,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Delete ${if (file.isDirectory) "folder" else "file"}?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Are you sure you want to delete \"${file.name}\"? This action cannot be undone.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun ConflictMenu(
    fileName: String,
    onReplace: () -> Unit,
    onAddSuffix: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "File conflict",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "A file named \"$fileName\" already exists in the destination. How would you like to proceed?",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onReplace,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Replace")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAddSuffix,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add suffix (Keep both)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
