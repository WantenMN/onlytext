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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    modifier: Modifier = Modifier
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
    var editorView by remember { mutableStateOf<StableEditorEditText?>(null) }
    var isEditorFocused by remember { mutableStateOf(false) }
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

    DisposableEffect(view, prefs) {
        val callback = object : WindowInsetsAnimationCompat.Callback(
            WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    isImeAnimationRunning = true
                }
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat = insets

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return

                isImeAnimationRunning = false
                val rootInsets = ViewCompat.getRootWindowInsets(view) ?: return
                if (!rootInsets.isVisible(WindowInsetsCompat.Type.ime())) return

                val stableImeHeightPx = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                if (stableImeHeightPx > 0 && stableImeHeightPx != cachedKeyboardHeightPx) {
                    cachedKeyboardHeightPx = stableImeHeightPx
                    prefs.edit().putInt(KEYBOARD_HEIGHT_PREF, stableImeHeightPx).apply()
                }
            }
        }

        ViewCompat.setWindowInsetsAnimationCallback(view, callback)
        onDispose {
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        val fallbackKeyboardHeightPx = (viewportHeightPx * 2f / 5f).toInt()
        val imeHeightPx = WindowInsets.ime.getBottom(density)
        val bottomInsetPx = when {
            imeHeightPx > 0 && !isImeAnimationRunning -> imeHeightPx
            cachedKeyboardHeightPx > 0 -> cachedKeyboardHeightPx
            else -> fallbackKeyboardHeightPx
        }
        val effectiveObscuredHeightPx = if (imeHeightPx > 0) bottomInsetPx else 0
        val safetyPaddingPx = with(density) { 24.dp.roundToPx() }

        LaunchedEffect(imeHeightPx, isImeAnimationRunning) {
            if (imeHeightPx > 0 && !isImeAnimationRunning && imeHeightPx != cachedKeyboardHeightPx) {
                cachedKeyboardHeightPx = imeHeightPx
                prefs.edit().putInt(KEYBOARD_HEIGHT_PREF, imeHeightPx).apply()
            }
        }

        LaunchedEffect(
            isEditorFocused,
            imeHeightPx,
            bottomInsetPx,
            textFieldValue.selection,
            textFieldValue.text,
            editorView
        ) {
            if (!isEditorFocused || effectiveObscuredHeightPx <= 0) return@LaunchedEffect

            val editText = editorView ?: return@LaunchedEffect
            val layout = editText.layout ?: return@LaunchedEffect
            val offset = editText.selectionEnd.coerceIn(0, editText.text?.length ?: 0)
            val line = layout.getLineForOffset(offset)
            val cursorTop = editText.top + editText.totalPaddingTop + layout.getLineTop(line)
            val cursorBottom = editText.top + editText.totalPaddingTop + layout.getLineBottom(line)

            val visibleHeightPx = (viewportHeightPx - effectiveObscuredHeightPx - safetyPaddingPx).coerceAtLeast(1)
            val visibleTopPx = scrollState.value
            val visibleBottomPx = visibleTopPx + visibleHeightPx

            val targetScroll = when {
                cursorBottom > visibleBottomPx -> cursorBottom - visibleHeightPx
                cursorTop < visibleTopPx -> cursorTop - safetyPaddingPx
                else -> null
            }?.coerceIn(0, scrollState.maxValue)

            if (targetScroll != null && targetScroll != scrollState.value) {
                scrollState.animateScrollTo(targetScroll)
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
                    .padding(bottom = with(density) { bottomInsetPx.toDp() })
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
        }
    }
}
