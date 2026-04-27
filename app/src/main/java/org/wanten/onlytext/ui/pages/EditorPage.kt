package org.wanten.onlytext.ui.pages

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

private const val KEYBOARD_HEIGHT_PREF = "editor_keyboard_height_px"

private class StableEditorEditText(context: Context) : EditText(context) {
    var suppressChangeDispatch = false
    var onValueChanged: ((TextFieldValue) -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var releasedToParent = false

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (suppressChangeDispatch) return

            onValueChanged?.invoke(
                TextFieldValue(
                    text = s?.toString().orEmpty(),
                    selection = TextRange(
                        selectionStart.coerceAtLeast(0),
                        selectionEnd.coerceAtLeast(0)
                    )
                )
            )
        }
    }

    init {
        addTextChangedListener(watcher)
    }

    override fun requestRectangleOnScreen(rectangle: Rect?): Boolean = false

    override fun requestRectangleOnScreen(rectangle: Rect?, immediate: Boolean): Boolean = false

    override fun bringPointIntoView(offset: Int): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                releasedToParent = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!releasedToParent &&
                    (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)
                ) {
                    releasedToParent = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                releasedToParent = false
            }
        }

        return super.onTouchEvent(event)
    }
}

@Composable
fun EditorPage(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    innerPadding: PaddingValues,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    lastSavedContent: String = "",
    onSave: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val prefs = remember(context) {
        context.getSharedPreferences("onlytext_prefs", Context.MODE_PRIVATE)
    }
    val inputMethodManager = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    var cachedKeyboardHeightPx by remember {
        mutableIntStateOf(prefs.getInt(KEYBOARD_HEIGHT_PREF, 0))
    }
    var isImeAnimationRunning by remember { mutableStateOf(false) }
    var isImeVisible by remember { mutableStateOf(false) }
    var isShowAnimation by remember { mutableStateOf(false) }
    var editorView by remember { mutableStateOf<StableEditorEditText?>(null) }
    var isEditorFocused by remember { mutableStateOf(false) }

    LaunchedEffect(view) {
        isImeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    val imeAnimationCallback = remember(view) {
        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    isImeAnimationRunning = true
                    isShowAnimation = !isImeVisible
                }
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat = insets

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    isImeAnimationRunning = false
                    isShowAnimation = false
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    isImeVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true

                    if (isImeVisible) {
                        val stableImeHeightPx = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                        if (stableImeHeightPx > 0 && stableImeHeightPx != cachedKeyboardHeightPx) {
                            cachedKeyboardHeightPx = stableImeHeightPx
                            prefs.edit().putInt(KEYBOARD_HEIGHT_PREF, stableImeHeightPx).apply()
                        }
                    }
                }
            }
        }
    }

    val focusEditorAtEnd = {
        val end = textFieldValue.text.length
        onValueChange(textFieldValue.copy(selection = TextRange(end)))
        editorView?.let { editText ->
            editText.isFocusable = true
            editText.isFocusableInTouchMode = true
            editText.requestFocus()
            editText.setSelection(end)
            inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        val minScrollableHeight: Dp = maxHeight + 1.dp
        val imeHeightPx = WindowInsets.ime.getBottom(density)
        val bottomInsetPx = imeHeightPx
        val bottomPaddingPx = with(density) { innerPadding.calculateBottomPadding().roundToPx() }
        val toolbarHeight = 40.dp
        val safetyPaddingPx = with(density) { 24.dp.roundToPx() }

        val showToolbar = isEditorFocused && imeHeightPx > 0 && (!isImeAnimationRunning || isShowAnimation)
        val toolbarPadding by animateDpAsState(
            targetValue = if (showToolbar) toolbarHeight else 0.dp,
            label = "toolbar_padding",
            animationSpec = if (showToolbar) androidx.compose.animation.core.spring() else snap()
        )

        LaunchedEffect(imeHeightPx, isImeAnimationRunning) {
            if (imeHeightPx > 0 && !isImeAnimationRunning && imeHeightPx != cachedKeyboardHeightPx) {
                cachedKeyboardHeightPx = imeHeightPx
                prefs.edit().putInt(KEYBOARD_HEIGHT_PREF, imeHeightPx).apply()
            }
        }

        LaunchedEffect(
            isEditorFocused,
            imeHeightPx,
            toolbarPadding,
            textFieldValue.selection,
            textFieldValue.text,
            editorView
        ) {
            if (!isEditorFocused || (imeHeightPx <= 0 && toolbarPadding <= 0.dp)) return@LaunchedEffect

            val editText = editorView ?: return@LaunchedEffect
            val layout = editText.layout ?: return@LaunchedEffect
            val offset = editText.selectionEnd.coerceIn(0, editText.text?.length ?: 0)
            val line = layout.getLineForOffset(offset)
            val cursorTop = editText.top + editText.totalPaddingTop + layout.getLineTop(line)
            val cursorBottom = editText.top + editText.totalPaddingTop + layout.getLineBottom(line)

            val currentObscuredPx = imeHeightPx + with(density) { toolbarPadding.roundToPx() }
            val visibleHeightPx = (viewportHeightPx - currentObscuredPx - safetyPaddingPx).coerceAtLeast(1)
            val visibleTopPx = scrollState.value
            val visibleBottomPx = visibleTopPx + visibleHeightPx

            val targetScroll = when {
                cursorBottom > visibleBottomPx -> cursorBottom - visibleHeightPx
                cursorTop < visibleTopPx -> cursorTop - safetyPaddingPx
                else -> null
            }

            if (targetScroll != null) {
                val clampedScroll = targetScroll.coerceIn(0, scrollState.maxValue)
                if (clampedScroll != scrollState.value) {
                    if (isImeAnimationRunning || (showToolbar && toolbarPadding < toolbarHeight)) {
                        scrollState.scrollTo(clampedScroll)
                    } else {
                        scrollState.animateScrollTo(clampedScroll)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusEditorAtEnd()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .heightIn(min = minScrollableHeight)
                    .padding(bottom = with(density) { bottomInsetPx.toDp() } + toolbarPadding)
            ) {
            if (textFieldValue.text.isEmpty()) {
                Text(
                    text = "Start typing...",
                    color = Color(hintColor),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    StableEditorEditText(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        minLines = 1
                        maxLines = Int.MAX_VALUE
                        gravity = Gravity.TOP or Gravity.START
                        background = null
                        setTextColor(textColor)
                        setHintTextColor(hintColor)
                        textSize = 18f
                        setPadding(
                            with(density) { 16.dp.roundToPx() },
                            with(density) { 12.dp.roundToPx() },
                            with(density) { 16.dp.roundToPx() },
                            with(density) { 12.dp.roundToPx() }
                        )
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                        isSingleLine = false
                        setHorizontallyScrolling(false)
                        overScrollMode = EditText.OVER_SCROLL_NEVER
                        setOnFocusChangeListener { _, hasFocus ->
                            isEditorFocused = hasFocus
                        }
                        ViewCompat.setWindowInsetsAnimationCallback(this, imeAnimationCallback)
                        editorView = this
                    }
                },
                update = { editText ->
                    editorView = editText
                    isEditorFocused = editText.hasFocus()
                    editText.onValueChanged = { next ->
                        if (next != textFieldValue) {
                            onValueChange(next)
                        }
                    }
                    if (editText.text?.toString() != textFieldValue.text) {
                        editText.suppressChangeDispatch = true
                        editText.setText(textFieldValue.text)
                        editText.suppressChangeDispatch = false
                    }

                    val end = textFieldValue.selection.end.coerceIn(0, textFieldValue.text.length)
                    val start = textFieldValue.selection.start.coerceIn(0, end)
                    if (editText.selectionStart != start || editText.selectionEnd != end) {
                        editText.setSelection(start, end)
                    }
                },
                onRelease = { editText ->
                    ViewCompat.setWindowInsetsAnimationCallback(editText, null)
                    editText.onValueChanged = null
                    if (editorView === editText) {
                        isEditorFocused = false
                    }
                    if (editorView === editText) {
                        editorView = null
                    }
                }
            )
            }

            AnimatedVisibility(
                visible = showToolbar,
                enter = fadeIn(animationSpec = tween(durationMillis = 1000, easing = EaseIn)),
                exit = fadeOut(animationSpec = snap()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        val stableHeight = if (showToolbar && cachedKeyboardHeightPx > 0) {
                            cachedKeyboardHeightPx
                        } else {
                            bottomInsetPx
                        }
                        IntOffset(0, -(stableHeight - bottomPaddingPx).coerceAtLeast(0))
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(toolbarHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { editorView?.onTextContextMenuItem(android.R.id.undo) },
                        modifier = Modifier.size(toolbarHeight)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { editorView?.onTextContextMenuItem(android.R.id.redo) },
                        modifier = Modifier.size(toolbarHeight)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val isSaved = textFieldValue.text == lastSavedContent
                    IconButton(
                        onClick = { if (!isSaved) onSave() },
                        enabled = !isSaved,
                        modifier = Modifier.size(toolbarHeight),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isSaved) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
