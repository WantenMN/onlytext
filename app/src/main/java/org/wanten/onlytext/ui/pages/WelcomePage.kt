package org.wanten.onlytext.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WelcomePage(
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
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
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Minimalist Text Editor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onOpenFolder,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Open Folder")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenFile,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Open File")
            }
        }
    }
}
