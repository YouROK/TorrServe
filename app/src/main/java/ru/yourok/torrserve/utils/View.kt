package ru.yourok.torrserve.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.google.android.material.internal.ViewUtils.hideKeyboard

inline fun <T> EditText.onDoneClickListener(
    crossinline onDoneClick: (text: String) -> T?
) = onActionClickListener(EditorInfo.IME_ACTION_DONE, onDoneClick)

inline val Context.inputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

fun View.hideKeyboard() = context
    .inputMethodManager
    .hideSoftInputFromWindow(this.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

inline fun <T> EditText.onActionClickListener(
    action: Int, crossinline onDoneClick: (text: String) -> T?
) = setOnEditorActionListener { _, actionId, _ ->
    if (actionId == action) {
        clearFocus()
        hideKeyboard()
        onDoneClick.invoke(text.toString())
        return@setOnEditorActionListener true
    }
    false
}