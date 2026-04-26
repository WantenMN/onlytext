package org.wanten.onlytext.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

class EditorState {
    var content by mutableStateOf("")
    val focusRequester = FocusRequester()
}

class ProjectState(initialName: String) {
    var projectName by mutableStateOf(initialName)
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
