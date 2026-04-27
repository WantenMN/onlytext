package org.wanten.onlytext.ui

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wanten.onlytext.ui.components.FileItem
import org.wanten.onlytext.ui.components.FileListView
import org.wanten.onlytext.ui.components.RecentFoldersDialog
import org.wanten.onlytext.ui.pages.EditorPage
import org.wanten.onlytext.ui.pages.FileManagerPage
import org.wanten.onlytext.ui.pages.ProjectWelcomePage
import org.wanten.onlytext.ui.pages.WelcomePage
import org.wanten.onlytext.ui.state.*
import org.wanten.onlytext.ui.utils.editorPageTransformer
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val state = remember { MainScreenState() }
    val initialPages = remember { state.load(context) }

    val horizontalPageCount = 4
    val verticalPageCount = 2
    
    val hCenterOffset = 500 * horizontalPageCount
    val vCenterOffset = 500 * verticalPageCount
    
    val hPagerState = rememberPagerState(
        initialPage = initialPages?.first?.let { savedHPage ->
            val actual = (savedHPage % horizontalPageCount + horizontalPageCount) % horizontalPageCount
            // Force return to editor if we were on a sidebar
            when (actual) {
                0 -> savedHPage + 1
                3 -> savedHPage - 1
                else -> savedHPage
            }
        } ?: (hCenterOffset + 1)
    ) { hCenterOffset * 2 }
    val vPagerState = rememberPagerState(initialPage = initialPages?.second ?: vCenterOffset) { vCenterOffset * 2 }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Create persistent, movable content for each logical page (2 rows x 4 columns).
    // movableContentOf ensures that UI state (like TextField scroll position and focus)
    // is preserved even when the content is moved to a different HorizontalPager index.
    val pageContents = remember(state) {
        List(verticalPageCount) { r ->
            List(horizontalPageCount) { c ->
                movableContentOf { hPage: Int, innerPadding: PaddingValues ->
                    RenderPageContent(
                        actualRow = r,
                        actualCol = c,
                        hPage = hPage,
                        hPagerState = hPagerState,
                        state = state,
                        innerPadding = innerPadding
                    )
                }
            }
        }
    }

    // Persistence logic
    LaunchedEffect(hPagerState.currentPage, vPagerState.currentPage) {
        delay(500)
        state.save(context, hPagerState.currentPage, vPagerState.currentPage)
    }

    // --- Start of Side-Effect Management ---
    // These effects run once per project/editor slot for the lifetime of MainScreen,
    // ensuring background tasks (polling, saving) aren't duplicated by Pager rendering.
    state.projects.forEachIndexed { r, row ->
        row.forEachIndexed { c, project ->
            val editor = state.editors[r][c]
            
            // Monitor project state changes for persistence
            LaunchedEffect(
                project.type, project.path, project.activeFilePath,
                project.expandedFolders, project.scrollIndex, project.scrollOffset,
                editor.textFieldValue, editor.scrollState.value
            ) {
                delay(500)
                state.save(context, hPagerState.currentPage, vPagerState.currentPage)
            }

            // Auto-save logic (1s debounce)
            LaunchedEffect(editor.textFieldValue.text) {
                val currentUriStr = project.activeFilePath
                if (currentUriStr != null && editor.textFieldValue.text != editor.lastSavedContent) {
                    delay(1000)
                    saveFileContent(context, project, editor, Uri.parse(currentUriStr), editor.textFieldValue.text)
                }
            }

            // Proactive Polling for external changes (every 2 seconds)
            LaunchedEffect(project.activeFilePath) {
                while (true) {
                    val currentUriStr = project.activeFilePath
                    if (currentUriStr != null) {
                        val uri = Uri.parse(currentUriStr)
                        try {
                            context.contentResolver.query(
                                uri,
                                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                                null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val modified = cursor.getLong(0)
                                    if (modified > project.lastModified) {
                                        loadFileContent(context, project, editor, uri)
                                    }
                                }
                            }
                        } catch (e: Exception) { }
                    }
                    delay(2000)
                }
            }

            // Lifecycle re-check
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, project.activeFilePath) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        project.activeFilePath?.let { loadFileContent(context, project, editor, Uri.parse(it)) }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }
    }
    // --- End of Side-Effect Management ---

    // Background sync for directory trees
    LaunchedEffect(state) {
        while (true) {
            state.projects.forEach { row ->
                row.forEach { project ->
                    if (project.type == ProjectType.DIRECTORY && project.path != null) {
                        val treeUri = Uri.parse(project.path!!)
                        try {
                            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                            val freshRoot = fetchChildren(context, treeUri, rootId, 0)
                            if (freshRoot != project.folderCache["root"]) {
                                project.folderCache["root"] = freshRoot
                            }
                            
                            project.expandedFolders.forEach { docId ->
                                val level = findLevel(project, docId)
                                val fresh = fetchChildren(context, treeUri, docId, level + 1)
                                if (fresh != project.folderCache[docId]) {
                                    project.folderCache[docId] = fresh
                                }
                            }
                        } catch (e: Exception) { }
                    }
                }
            }
            delay(15000)
        }
    }

    LaunchedEffect(hPagerState, vPagerState) {
        snapshotFlow { hPagerState.currentPage to vPagerState.currentPage }.collect { _ ->
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(hPagerState) {
        snapshotFlow { hPagerState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                val current = hPagerState.currentPage
                val actual = (current % horizontalPageCount + horizontalPageCount) % horizontalPageCount
                val target = hCenterOffset + actual
                if ((current - target).absoluteValue >= 100) {
                    hPagerState.scrollToPage(target)
                }
            }
        }
    }

    LaunchedEffect(vPagerState) {
        snapshotFlow { vPagerState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                val current = vPagerState.currentPage
                val actual = (current % verticalPageCount + verticalPageCount) % verticalPageCount
                val target = vCenterOffset + actual
                if ((current - target).absoluteValue >= 100) {
                    vPagerState.scrollToPage(target)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .twoFingerVerticalScroll(vPagerState, scope)
        ) {
            // Dummy VerticalPager to anchor vPagerState and handle coordinate transformations
            VerticalPager(
                state = vPagerState,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0f },
                userScrollEnabled = false
            ) { Box(Modifier.fillMaxSize()) }

            HorizontalPager(
                state = hPagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                beyondViewportPageCount = 1
            ) { hPage ->
                val actualCol = (hPage % horizontalPageCount + horizontalPageCount) % horizontalPageCount
                val isSidebar = actualCol == 0 || actualCol == 3

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isSidebar) 1f else 0f)
                        .editorPageTransformer(hPage, hPagerState, actualCol)
                ) {
                    // To preserve scroll state and focus, we must keep both logical rows in the composition tree.
                    // We calculate which absolute pages correspond to actualRow 0 and 1 near the current scroll position.
                    for (rowIdx in 0 until verticalPageCount) {
                        val actualRow = rowIdx
                        key(actualRow) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val currentPosition =
                                        vPagerState.currentPage + vPagerState.currentPageOffsetFraction
                                    val absolutePage = findNearestAbsolutePageForRow(
                                        actualRow = actualRow,
                                        rowCount = verticalPageCount,
                                        currentPosition = currentPosition
                                    )

                                    translationY = (absolutePage - currentPosition) * size.height
                                }
                            ) {
                                pageContents[actualRow][actualCol](hPage, innerPadding)
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun findNearestAbsolutePageForRow(
    actualRow: Int,
    rowCount: Int,
    currentPosition: Float
): Int {
    val floorPage = floor(currentPosition).toInt()
    val normalizedRow = ((actualRow % rowCount) + rowCount) % rowCount

    val baseCandidate = floorPage - floorMod(floorPage - normalizedRow, rowCount)
    val previousCandidate = baseCandidate - rowCount
    val nextCandidate = baseCandidate + rowCount

    return listOf(previousCandidate, baseCandidate, nextCandidate)
        .minByOrNull { abs(it - currentPosition) }
        ?: baseCandidate
}

private fun floorMod(value: Int, mod: Int): Int {
    val result = value % mod
    return if (result >= 0) result else result + mod
}

@Composable
private fun RenderPageContent(
    actualRow: Int,
    actualCol: Int,
    hPage: Int,
    hPagerState: PagerState,
    state: MainScreenState,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showRecentFolders by remember { mutableStateOf(false) }

    val projectIndex = if (actualCol == 0 || actualCol == 1) 0 else 1
    val project = state.projects[actualRow][projectIndex]
    val editor = state.editors[actualRow][projectIndex]
    
    val openFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: Exception) { }

            project.type = ProjectType.DIRECTORY
            project.path = it.toString()
            project.activeFilePath = null
            state.recentFoldersManager.add(it.toString())

            // Auto switch to FileManager
            scope.launch {
                val targetPage = if (actualCol == 1) hPage - 1 else if (actualCol == 2) hPage + 1 else hPage
                if (targetPage != hPage) {
                    hPagerState.animateScrollToPage(targetPage)
                }
            }
        }
    }

    fun selectFile(fileItem: FileItem) {
        if (!fileItem.isDirectory) {
            val docUri = project.path?.let {
                val treeUri = Uri.parse(it)
                DocumentsContract.buildDocumentUriUsingTree(treeUri, fileItem.path)
            } ?: return
            
            if (loadFileContent(context, project, editor, docUri)) {
                scope.launch {
                    val targetPage = if (actualCol == 0) hPage + 1 else hPage - 1
                    hPagerState.animateScrollToPage(targetPage)
                }
            }
        }
    }

    when (actualCol) {
        0, 3 -> {
            FileManagerPage(
                project = project,
                recentFoldersManager = state.recentFoldersManager,
                onShowRecentFolders = { showRecentFolders = it },
                contentPadding = innerPadding,
                onFileSelected = { fileItem ->
                    selectFile(fileItem)
                },
                onOpenFolderClick = { openFolderLauncher.launch(null) },
                onCloseFolderClick = {
                    project.type = ProjectType.NONE
                    project.path = null
                    project.activeFilePath = null
                }
            )
        }
        1, 2 -> {
            if (project.type == ProjectType.NONE) {
                WelcomePage(
                    editorIndex = project.projectName,
                    onOpenFolder = { openFolderLauncher.launch(null) },
                    onShowRecentFolders = { showRecentFolders = true },
                    innerPadding = innerPadding
                )
            } else if (project.activeFilePath == null) {
                ProjectWelcomePage(
                    editorIndex = project.projectName,
                    onSelectFile = {
                        scope.launch {
                            val targetPage = if (actualCol == 1) hPage - 1 else hPage + 1
                            hPagerState.animateScrollToPage(targetPage)
                        }
                    },
                    innerPadding = innerPadding
                )
            } else {
                val relativePath = remember<String?>(project.path, project.activeFilePath, project.activeFileName) {
                    calculateRelativePath(project)
                }
                EditorPage(
                    textFieldValue = editor.textFieldValue,
                    onValueChange = { editor.textFieldValue = it },
                    innerPadding = innerPadding,
                    focusRequester = editor.focusRequester,
                    scrollState = editor.scrollState,
                    relativeFilePath = relativePath,
                    lastSavedContent = editor.lastSavedContent,
                    onSave = {
                        project.activeFilePath?.let { uriStr ->
                            saveFileContent(context, project, editor, Uri.parse(uriStr), editor.textFieldValue.text)
                        }
                    }
                )
            }
        }
    }

    if (showRecentFolders) {
        RecentFoldersDialog(
            recentFolders = state.recentFoldersManager.recentFolders,
            onFolderSelected = { path ->
                state.recentFoldersManager.add(path)
                state.recentFoldersManager.save(context)
                project.type = ProjectType.DIRECTORY
                project.path = path
                project.activeFilePath = null
                showRecentFolders = false

                // If on Editor side, switch to File Manager
                if (actualCol == 1 || actualCol == 2) {
                    scope.launch {
                        val targetPage = if (actualCol == 1) hPage - 1 else hPage + 1
                        hPagerState.animateScrollToPage(targetPage)
                    }
                }
            },
            onRemoveFolder = { path ->
                state.recentFoldersManager.remove(path)
                state.recentFoldersManager.save(context)
            },
            onClearAll = {
                state.recentFoldersManager.clear()
                state.recentFoldersManager.save(context)
            },
            onDismissRequest = { showRecentFolders = false }
        )
    }
}

private fun loadFileContent(context: android.content.Context, project: ProjectState, editor: EditorState, uri: Uri): Boolean {
    try {
        val isText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(1024)
            val read = inputStream.read(buffer)
            if (read <= 0) true
            else {
                var binary = false
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) {
                        binary = true
                        break
                    }
                }
                !binary
            }
        } ?: false

        if (!isText) return false

        if (project.activeFilePath != uri.toString()) {
            project.activeFilePath = uri.toString()
            editor.scrollState = ScrollState(0)
        }
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIndex != -1) {
                    project.activeFileName = cursor.getString(nameIndex)
                }
                val modIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (modIndex != -1) {
                    project.lastModified = cursor.getLong(modIndex)
                }
            }
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val content = inputStream.bufferedReader().readText()
            if (content != editor.textFieldValue.text) {
                val newSelection = if (editor.textFieldValue.selection.end <= content.length) {
                    editor.textFieldValue.selection
                } else {
                    TextRange(content.length)
                }
                editor.textFieldValue = TextFieldValue(content, newSelection)
                editor.lastSavedContent = content
            }
        }
        return true
    } catch (e: Exception) {
        if (project.activeFilePath == uri.toString()) {
            editor.textFieldValue = TextFieldValue("Error loading file: ${e.message}")
        }
        return false
    }
}

private fun saveFileContent(context: android.content.Context, project: ProjectState, editor: EditorState, uri: Uri, content: String) {
    if (content == editor.lastSavedContent) return
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.write(content.toByteArray())
            editor.lastSavedContent = content
            
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    project.lastModified = cursor.getLong(0)
                }
            }
        }
    } catch (e: Exception) { }
}

fun Modifier.twoFingerVerticalScroll(
    pagerState: PagerState,
    scope: CoroutineScope
): Modifier = pointerInput(pagerState) {
    awaitPointerEventScope {
        while (true) {
            val startEvent = awaitPointerEvent(PointerEventPass.Initial)
            if (startEvent.changes.size >= 2) {
                var isVerticalIntent: Boolean? = null
                var totalDragX = 0f
                var totalDragY = 0f
                var netDragY = 0f
                val touchSlop = 10f
                val startTime = System.currentTimeMillis()

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val changes = event.changes
                    if (changes.all { it.changedToUp() }) break

                    val dragDeltaX = changes.map { it.positionChange().x }.sum()
                    val dragDeltaY = changes.map { it.positionChange().y }.sum()
                    netDragY += dragDeltaY

                    if (isVerticalIntent == null) {
                        totalDragX += abs(dragDeltaX)
                        totalDragY += abs(dragDeltaY)
                        if (totalDragX > touchSlop || totalDragY > touchSlop) {
                            isVerticalIntent = totalDragY > totalDragX
                        }
                    }

                    when (isVerticalIntent) {
                        true -> {
                            val avgDeltaY = changes.map { it.positionChange().y }.average().toFloat()
                            val sensitivity = 3.0f
                            pagerState.dispatchRawDelta(-avgDeltaY * sensitivity)
                            changes.forEach { it.consume() }
                        }
                        false -> { }
                        null -> {
                            changes.forEach { it.consume() }
                        }
                    }
                }

                if (isVerticalIntent == true) {
                    val duration = System.currentTimeMillis() - startTime
                    scope.launch {
                        val quickSwipeThreshold = 500
                        val currentPos = pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction
                        
                        val targetPage = when {
                            duration < quickSwipeThreshold && abs(netDragY) > 5f -> {
                                if (netDragY < 0) floor(currentPos).toInt() + 1
                                else ceil(currentPos).toInt() - 1
                            }
                            else -> currentPos.roundToInt()
                        }
                        pagerState.animateScrollToPage(targetPage)
                    }
                }
            }
        }
    }
}

private fun calculateRelativePath(project: ProjectState): String? {
    val treeUriStr = project.path ?: return project.activeFileName
    val docUriStr = project.activeFilePath ?: return project.activeFileName
    
    return try {
        val treeUri = Uri.parse(treeUriStr)
        val docUri = Uri.parse(docUriStr)
        
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val docId = DocumentsContract.getDocumentId(docUri)
        
        if (docId.startsWith(treeId)) {
            var relative = docId.substring(treeId.length)
            while (relative.startsWith("/") || relative.startsWith(":")) {
                relative = relative.substring(1)
            }
            if (relative.isEmpty()) project.activeFileName else relative
        } else {
            project.activeFileName
        }
    } catch (e: Exception) {
        project.activeFileName
    }
}

