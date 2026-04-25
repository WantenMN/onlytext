package org.wanten.onlytext.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

class MainScreenState {
    var leftNoteContent by mutableStateOf("")
    var rightNoteContent by mutableStateOf("")
    
    val leftFocusRequester = FocusRequester()
    val rightFocusRequester = FocusRequester()
}

@Composable
fun rememberMainScreenState(): MainScreenState {
    return remember { MainScreenState() }
}
