package org.wanten.onlytext.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

import androidx.compose.runtime.mutableStateMapOf
import org.wanten.onlytext.ui.components.FileItem

class EditorState {
    var content by mutableStateOf("")
    var lastSavedContent by mutableStateOf("")
    val focusRequester = FocusRequester()
}

enum class ProjectType {
    NONE, FILE, DIRECTORY
}

class ProjectState(initialName: String) {
    var projectName by mutableStateOf(initialName)
    var type by mutableStateOf(ProjectType.NONE)
    
    var activeFilePath by mutableStateOf<String?>(null)
    var activeFileName by mutableStateOf<String?>(null)
    var lastModified by mutableStateOf(0L)
    
    private var _path by mutableStateOf<String?>(null)
    var path: String?
        get() = _path
        set(value) {
            if (_path != value) {
                _path = value
                folderCache.clear()
                expandedFolders = emptySet()
                scrollIndex = 0
                scrollOffset = 0
            }
        }
    
    // Cache for children of each folder (DocumentID -> List<FileItem>)
    val folderCache = mutableStateMapOf<String, List<FileItem>>()
    // Set of expanded folder DocumentIDs
    var expandedFolders by mutableStateOf(setOf<String>())

    // Scroll position
    var scrollIndex by mutableStateOf(0)
    var scrollOffset by mutableStateOf(0)
}

class MainScreenState {
    // 2 Rows x 2 Columns of Editors
    val editors = listOf(
        listOf(EditorState(), EditorState()), // Row 0: Left, Right
        listOf(EditorState(), EditorState())  // Row 1: Left, Right
    )

    // 2 Rows x 2 Columns of File Managers / Projects
    val projects = listOf(
        listOf(ProjectState("1"), ProjectState("2")),
        listOf(ProjectState("3"), ProjectState("4"))
    )
}

@Composable
fun rememberMainScreenState(): MainScreenState {
    return remember { MainScreenState() }
}
