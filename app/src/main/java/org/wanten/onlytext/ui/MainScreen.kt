package org.wanten.onlytext.ui

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val state = rememberMainScreenState()
    
    val horizontalPageCount = 4
    val verticalPageCount = 2
    
    val hCenterOffset = 500 * horizontalPageCount
    val vCenterOffset = 500 * verticalPageCount
    
    val hPagerState = rememberPagerState(initialPage = hCenterOffset + 1) { hCenterOffset * 2 }
    val vPagerState = rememberPagerState(initialPage = vCenterOffset) { vCenterOffset * 2 }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

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
            ) { hPage ->
                val actualCol = (hPage % horizontalPageCount + horizontalPageCount) % horizontalPageCount
                val isSidebar = actualCol == 0 || actualCol == 3

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isSidebar) 1f else 0f)
                        .editorPageTransformer(hPage, hPagerState, actualCol)
                ) {
                    val offsetFraction = vPagerState.currentPageOffsetFraction
                    
                    val rowsToRender = if (offsetFraction > 0) {
                        listOf(0 to vPagerState.currentPage, 1 to vPagerState.currentPage + 1)
                    } else if (offsetFraction < 0) {
                        listOf(-1 to vPagerState.currentPage - 1, 0 to vPagerState.currentPage)
                    } else {
                        listOf(0 to vPagerState.currentPage)
                    }

                    rowsToRender.forEach { (offsetIndex, absolutePage) ->
                        val actualRow = (absolutePage % verticalPageCount + verticalPageCount) % verticalPageCount
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = (offsetIndex - offsetFraction) * size.height
                            }
                        ) {
                            RenderPageContent(
                                actualRow = actualRow,
                                actualCol = actualCol,
                                hPage = hPage,
                                hPagerState = hPagerState,
                                state = state,
                                innerPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = innerPadding.calculateBottomPadding()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
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

    val projectIndex = if (actualCol == 0 || actualCol == 1) 0 else 1
    val project = state.projects[actualRow][projectIndex]
    val editor = state.editors[actualRow][projectIndex]
    
    fun loadFileContent(uri: Uri) {
        // 1. Immediately set active path so UI switches from Welcome to Editor
        project.activeFilePath = uri.toString()
        
        try {
            // 2. Try to get real display name and metadata
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

            // 3. Load actual text content
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().readText()
                if (content != editor.content) {
                    editor.content = content
                    editor.lastSavedContent = content
                }
            }
        } catch (e: Exception) {
            // Show error in editor so user knows what happened
            editor.content = "Error loading file: ${e.message}"
        }
    }

    fun saveFileContent(uri: Uri, content: String) {
        if (content == editor.lastSavedContent) return
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray())
                editor.lastSavedContent = content
                
                // Update local timestamp after save to avoid immediate re-load from poll
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

    // Auto-save logic (1s debounce)
    LaunchedEffect(editor.content) {
        val currentUriStr = project.activeFilePath
        if (currentUriStr != null && editor.content != editor.lastSavedContent) {
            delay(1000)
            saveFileContent(Uri.parse(currentUriStr), editor.content)
        }
    }

    // Proactive Polling for external changes (every 5 seconds)
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
                            // If system time is newer than our recorded time, reload
                            if (modified > project.lastModified) {
                                loadFileContent(uri)
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
                project.activeFilePath?.let { loadFileContent(Uri.parse(it)) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            // Take persistable permission for auto-save and polling
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: Exception) { }

            project.type = ProjectType.FILE
            project.path = it.toString()
            loadFileContent(it)
        }
    }
    
    val openFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            // Take persistable permission for folder traversal
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: Exception) { }

            project.type = ProjectType.DIRECTORY
            project.path = it.toString()
            project.activeFilePath = null
        }
    }

    fun handleCreateFile() {
        project.path?.let { path ->
            val rootUri = Uri.parse(path)
            try {
                val rootId = DocumentsContract.getTreeDocumentId(rootUri)
                val newFileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(rootUri, rootId),
                    "text/plain",
                    "Untitled.txt"
                )
                newFileUri?.let {
                    editor.content = ""
                    project.activeFilePath = it.toString()
                    project.activeFileName = it.lastPathSegment ?: "Untitled.txt"
                    project.folderCache.remove("root")
                    loadFileContent(it) // Init timestamp
                }
            } catch (e: Exception) { }
        }
    }

    when (actualCol) {
        0, 3 -> {
            FileManagerPage(
                project = project,
                contentPadding = innerPadding,
                onFileSelected = { fileItem ->
                    if (!fileItem.isDirectory) {
                        val docUri = if (project.type == ProjectType.DIRECTORY) {
                            val treeUri = Uri.parse(project.path)
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, fileItem.path)
                        } else {
                            Uri.parse(fileItem.path)
                        }
                        loadFileContent(docUri)
                        scope.launch {
                            val targetPage = if (actualCol == 0) hPage + 1 else hPage - 1
                            hPagerState.animateScrollToPage(targetPage)
                        }
                    }
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
                    onOpenFile = { openFileLauncher.launch(arrayOf("text/plain")) },
                    onOpenFolder = { openFolderLauncher.launch(null) },
                    innerPadding = innerPadding
                )
            } else if (project.activeFilePath == null) {
                ProjectWelcomePage(
                    onCreateFile = { handleCreateFile() },
                    onSelectFile = {
                        scope.launch {
                            val targetPage = if (actualCol == 1) hPage - 1 else hPage + 1
                            hPagerState.animateScrollToPage(targetPage)
                        }
                    },
                    innerPadding = innerPadding
                )
            } else {
                EditorPage(
                    text = editor.content,
                    onTextChange = { editor.content = it },
                    innerPadding = innerPadding,
                    focusRequester = editor.focusRequester
                )
            }
        }
    }
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
