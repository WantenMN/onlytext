package org.wanten.onlytext.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFoldersDialog(
    recentFolders: List<String>,
    onFolderSelected: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredFolders = remember(recentFolders, searchQuery) {
        if (searchQuery.isBlank()) {
            recentFolders
        } else {
            recentFolders.filter { path ->
                val displayName = getDisplayName(path)
                displayName.contains(searchQuery, ignoreCase = true) || path.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (recentFolders.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (filteredFolders.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isEmpty()) "No recent folders" else "No matches found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredFolders) { path ->
                                RecentFolderItem(
                                    path = path,
                                    onClick = { onFolderSelected(path) },
                                    onRemove = { onRemoveFolder(path) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search folders...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    }
}

@Composable
fun RecentFolderItem(
    path: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val displayName = remember(path) { getDisplayName(path) }
    val displayPath = remember(path) { getFormattedDisplayPath(path) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private fun getFormattedDisplayPath(path: String): String {
    return try {
        val uri = Uri.parse(path)
        val segment = uri.lastPathSegment ?: ""
        segment.replace("primary:", "").replace("%3A", ":").replace("%2F", "/")
    } catch (e: Exception) {
        path
    }
}

private fun getDisplayName(path: String): String {
    return getFormattedDisplayPath(path).substringAfterLast('/')
}
