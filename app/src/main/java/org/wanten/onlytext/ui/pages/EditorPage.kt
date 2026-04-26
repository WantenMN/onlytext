package org.wanten.onlytext.ui.pages

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun EditorPage(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    innerPadding: PaddingValues,
    focusRequester: FocusRequester,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    TextField(
        value = textFieldValue,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
            .focusRequester(focusRequester)
            .verticalScroll(scrollState),
        placeholder = { Text("Start typing...") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
