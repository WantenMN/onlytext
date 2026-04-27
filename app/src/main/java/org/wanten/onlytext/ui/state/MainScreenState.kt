package org.wanten.onlytext.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.wanten.onlytext.ui.components.FileItem

suspend fun fetchChildren(context: Context, treeUri: Uri, documentId: String, level: Int): List<FileItem> = withContext(Dispatchers.IO) {
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
                
                children.add(FileItem(name, isDir, id, level = level))
            }
        }
    } catch (e: Exception) {
        return@withContext listOf(FileItem("Error: ${e.message}", false, "error_$documentId", level = level))
    }
    children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

fun findLevel(project: ProjectState, docId: String): Int {
    if (docId == "root") return 0
    project.folderCache.values.forEach { list ->
        list.find { it.path == docId }?.let { return it.level }
    }
    return 0
}

class EditorState {
    var textFieldValue by mutableStateOf(TextFieldValue(""))
    var lastSavedContent by mutableStateOf("")
    val focusRequester = FocusRequester()
    var scrollState by mutableStateOf(ScrollState(0))

    fun save(context: Context, keyPrefix: String) {
        val prefs = context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${keyPrefix}_text", textFieldValue.text)
            .putInt("${keyPrefix}_sel_start", textFieldValue.selection.start)
            .putInt("${keyPrefix}_sel_end", textFieldValue.selection.end)
            .putInt("${keyPrefix}_scroll", scrollState.value)
            .apply()
    }

    fun load(context: Context, keyPrefix: String) {
        val prefs = context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
        val text = prefs.getString("${keyPrefix}_text", "") ?: ""
        val selStart = prefs.getInt("${keyPrefix}_sel_start", 0)
        val selEnd = prefs.getInt("${keyPrefix}_sel_end", 0)
        val scroll = prefs.getInt("${keyPrefix}_scroll", 0)
        textFieldValue = TextFieldValue(text, TextRange(selStart, selEnd))
        lastSavedContent = text
        scrollState = ScrollState(scroll)
    }
}

enum class ProjectType {
    NONE, FILE, DIRECTORY
}

class ProjectState(initialName: String) {
    var projectName by mutableStateOf(initialName)
    var type by mutableStateOf(ProjectType.NONE)
    
    var activeFilePath by mutableStateOf<String?>(null)
    var activeFileName by mutableStateOf<String?>(null)
    var lastModified by mutableLongStateOf(0L)
    
    var lazyListState by mutableStateOf(LazyListState())

    private var _path by mutableStateOf<String?>(null)
    var path: String?
        get() = _path
        set(value) {
            _path = value
            folderCache.clear()
            expandedFolders = emptySet()
            scrollIndex = 0
            scrollOffset = 0
            lazyListState = LazyListState(0, 0)
            isRecursiveExpanding = false
        }
    
    // Cache for children of each folder (DocumentID -> List<FileItem>)
    val folderCache = mutableStateMapOf<String, List<FileItem>>()
    // Set of expanded folder DocumentIDs
    var expandedFolders by mutableStateOf(setOf<String>())

    // Scroll position
    var scrollIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    var isRecursiveExpanding by mutableStateOf(false)
    var skippedFolders by mutableStateOf(setOf<String>())

    fun save(context: Context, keyPrefix: String) {
        val prefs = context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
        val edit = prefs.edit()
            .putString("${keyPrefix}_type", type.name)
            .putString("${keyPrefix}_path", path)
            .putString("${keyPrefix}_activeFilePath", activeFilePath)
            .putString("${keyPrefix}_activeFileName", activeFileName)
            .putLong("${keyPrefix}_lastModified", lastModified)
            .putStringSet("${keyPrefix}_expandedFolders", expandedFolders)
            .putInt("${keyPrefix}_scrollIndex", scrollIndex)
            .putInt("${keyPrefix}_scrollOffset", scrollOffset)
        
        val cacheObj = JSONObject()
        folderCache.forEach { (key, items) ->
            val array = JSONArray()
            items.forEach { item ->
                val itemObj = JSONObject()
                itemObj.put("n", item.name)
                itemObj.put("d", item.isDirectory)
                itemObj.put("p", item.path)
                itemObj.put("l", item.level)
                array.put(itemObj)
            }
            cacheObj.put(key, array)
        }
        edit.putString("${keyPrefix}_folderCache", cacheObj.toString())
        edit.apply()
    }

    fun load(context: Context, keyPrefix: String) {
        val prefs = context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
        type = ProjectType.valueOf(prefs.getString("${keyPrefix}_type", ProjectType.NONE.name) ?: ProjectType.NONE.name)
        _path = prefs.getString("${keyPrefix}_path", null)
        activeFilePath = prefs.getString("${keyPrefix}_activeFilePath", null)
        activeFileName = prefs.getString("${keyPrefix}_activeFileName", null)
        lastModified = prefs.getLong("${keyPrefix}_lastModified", 0L)
        expandedFolders = prefs.getStringSet("${keyPrefix}_expandedFolders", emptySet()) ?: emptySet()
        scrollIndex = prefs.getInt("${keyPrefix}_scrollIndex", 0)
        scrollOffset = prefs.getInt("${keyPrefix}_scrollOffset", 0)
        lazyListState = LazyListState(scrollIndex, scrollOffset)
        
        val cacheStr = prefs.getString("${keyPrefix}_folderCache", null)
        if (cacheStr != null) {
            try {
                val cacheObj = JSONObject(cacheStr)
                cacheObj.keys().forEach { key ->
                    val array = cacheObj.getJSONArray(key)
                    val items = mutableListOf<FileItem>()
                    for (i in 0 until array.length()) {
                        val itemObj = array.getJSONObject(i)
                        items.add(FileItem(
                            name = itemObj.getString("n"),
                            isDirectory = itemObj.getBoolean("d"),
                            path = itemObj.getString("p"),
                            level = itemObj.getInt("l") // Fix key to match save
                        ))
                    }
                    folderCache[key] = items
                }
            } catch (e: Exception) {}
        }
        sanitizeExpandedFolders()
    }

    private fun sanitizeExpandedFolders() {
        if (folderCache.isEmpty()) return
        val reachable = mutableSetOf<String>()
        fun check(parentId: String) {
            folderCache[parentId]?.forEach { item ->
                if (item.isDirectory && expandedFolders.contains(item.path)) {
                    reachable.add(item.path)
                    check(item.path)
                }
            }
        }
        check("root")
        expandedFolders = reachable
    }

    fun renameFile(context: Context, file: FileItem, newName: String, scope: CoroutineScope) {
        val treeUri = Uri.parse(path ?: return)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.path)
        scope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.renameDocument(context.contentResolver, documentUri, newName)
                refreshParent(context, file.path)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteFile(context: Context, file: FileItem, scope: CoroutineScope) {
        val treeUri = Uri.parse(path ?: return)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.path)
        scope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, documentUri)
                refreshParent(context, file.path)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun copyFile(context: Context, file: FileItem, scope: CoroutineScope) {
        val treeUri = Uri.parse(path ?: return)
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.path)
        val parentPath = findParentPath(file.path) ?: "root"
        val parentId = if (parentPath == "root") DocumentsContract.getTreeDocumentId(treeUri) else parentPath
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        
        scope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.copyDocument(context.contentResolver, sourceUri, parentUri)
                refreshFolder(context, parentPath)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun moveFile(context: Context, file: FileItem, targetParentPath: String, scope: CoroutineScope) {
        val treeUri = Uri.parse(path ?: return)
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.path)
        val sourceParentPath = findParentPath(file.path) ?: "root"
        val sourceParentId = if (sourceParentPath == "root") DocumentsContract.getTreeDocumentId(treeUri) else sourceParentPath
        val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sourceParentId)
        
        val targetParentId = if (targetParentPath == "root") DocumentsContract.getTreeDocumentId(treeUri) else targetParentPath
        val targetParentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetParentId)

        scope.launch(Dispatchers.IO) {
            try {
                DocumentsContract.moveDocument(context.contentResolver, sourceUri, sourceParentUri, targetParentUri)
                refreshFolder(context, sourceParentPath)
                refreshFolder(context, targetParentPath)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Move failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun findParentPath(childPath: String): String? {
        folderCache.forEach { (parentPath, children) ->
            if (children.any { it.path == childPath }) return parentPath
        }
        return null
    }

    private suspend fun refreshParent(context: Context, childPath: String) {
        val parentPath = findParentPath(childPath) ?: "root"
        refreshFolder(context, parentPath)
    }

    private suspend fun refreshFolder(context: Context, folderPath: String) {
        val treeUri = Uri.parse(path ?: return)
        val docId = if (folderPath == "root") DocumentsContract.getTreeDocumentId(treeUri) else folderPath
        val level = findLevel(this, docId)
        val newChildren = fetchChildren(context, treeUri, docId, level)
        withContext(Dispatchers.Main) {
            folderCache[folderPath] = newChildren
        }
    }
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

    fun save(context: Context, hPage: Int, vPage: Int) {
        val prefs = context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("hPage", hPage)
            .putInt("vPage", vPage)
            .apply()
        
        for (r in 0..1) {
            for (c in 0..1) {
                projects[r][c].save(context, "proj_${r}_${c}")
                editors[r][c].save(context, "edit_${r}_${c}")
            }
        }
    }

    fun load(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("hPage")) return null
        
        val hPage = prefs.getInt("hPage", -1)
        val vPage = prefs.getInt("vPage", -1)
        
        for (r in 0..1) {
            for (c in 0..1) {
                projects[r][c].load(context, "proj_${r}_${c}")
                editors[r][c].load(context, "edit_${r}_${c}")
            }
        }
        return hPage to vPage
    }
}

