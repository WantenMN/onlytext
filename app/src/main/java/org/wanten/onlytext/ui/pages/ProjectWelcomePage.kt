package org.wanten.onlytext.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProjectWelcomePage(
    onCreateFile: () -> Unit,
    onSelectFile: () -> Unit,
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "OnlyText",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Folder is open, but no file is selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onCreateFile,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Create New File")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSelectFile,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Select a File to Edit")
            }
        }
    }
}
