package com.android.xrayfa.ui.component


import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A reusable editable dialog.
 *
 * @param initialText The initial text content.
 * @param title The dialog title (nullable).
 * @param label The label for the TextField (nullable).
 * @param confirmText The text for the confirm button.
 * @param dismissText The text for the dismiss/cancel button.
 * @param validator A validation function: returns null if valid, otherwise an error message.
 * @param isConfirmLoading Whether the confirm button should display a loading state (for async saving).
 * @param onConfirm Callback invoked when the confirm button is clicked and validation passes (provides the updated text).
 * @param onDismiss Callback invoked when the dialog is dismissed or canceled.
 */

@Composable
fun EditTextDialog(
    initialText: String,
    isNumeric: Boolean = false,
    title: String? = "",
    label: String? = null,
    confirmText: String = "",
    dismissText: String = "",
    validator: (String) -> String? = {null},
    isConfirmLoading: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    //var text by remember { mutableStateOf(initialText) }
    var textValue by remember {
        mutableStateOf(TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length)
        ))
    }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Small delay to ensure dialog is composed before requesting focus
        delay(120)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            focusManager.clearFocus()
            hideKeyboard(context)
            onDismiss()
        },
        title = {
            if (!title.isNullOrEmpty()) {
                Text(text = title)
            }
        },
        text = {
            Column {
                TextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        if (isNumeric) {
                            if (newValue.text.all { c -> c.isDigit() }) {
                                textValue = newValue
                            }
                        }else {
                            textValue = newValue
                        }
                        error = validator(newValue.text)
                    },
                    label = { if (!label.isNullOrEmpty()) Text(label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isNumeric) KeyboardType.Number
                        else KeyboardType.Text
                    ),
                )
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = androidx.compose.ui.graphics.Color.Red,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val currentText = textValue.text
                    val validation = validator(currentText)
                    if (validation == null) {
                        focusManager.clearFocus()
                        hideKeyboard(context)
                        onConfirm(currentText)
                    } else {
                        error = validation
                    }
                },
                enabled = !isConfirmLoading
            ) {
                if (isConfirmLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp).then(Modifier))
                }
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    hideKeyboard(context)
                    onDismiss()
                }
            ) {
                Text(text = dismissText)
            }
        },
    )
}

private fun hideKeyboard(context: android.content.Context) {
    val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
    val activity = context as? Activity
    val view = activity?.currentFocus
    if (view != null && imm != null) {
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}



