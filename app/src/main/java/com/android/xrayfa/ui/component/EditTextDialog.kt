package com.android.xrayfa.ui.component


import android.app.Activity
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 可复用的编辑弹窗
 *
 * @param initialText 初始文本
 * @param title 弹窗标题（可空）
 * @param label TextField 的 label（可空）
 * @param confirmText 确认按钮文本
 * @param dismissText 取消按钮文本
 * @param validator 校验函数：返回 null 表示合法，否则返回错误提示
 * @param isConfirmLoading 确认按钮是否显示 loading（用于异步保存）
 * @param onConfirm 点击确认并通过校验时回调（传入最新文本）
 * @param onDismiss 取消或关闭时回调
 */
@Composable
fun EditTextDialog(
    initialText: String,
    isNumeric: Boolean = false,
    title: String? = "编辑",
    label: String? = null,
    confirmText: String = "保存",
    dismissText: String = "取消",
    validator: (String) -> String? = { if (it.isBlank()) "不能为空" else null },
    isConfirmLoading: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(initialText) }
    var error by remember { mutableStateOf<String?>(null) }

    // 保证 dialog 一出现就请求焦点并弹出键盘
    LaunchedEffect(Unit) {
        // Small delay to ensure dialog is composed before requesting focus
        delay(120)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            // 隐藏键盘然后触发 onDismiss
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
                    value = text,
                    onValueChange = {
                        if (isNumeric) {
                            if (it.all { c -> c.isDigit() }) {
                                text = it
                            }
                        }else {
                            text = it
                        }
                        error = validator(it)
                    },
                    label = { if (!label.isNullOrEmpty()) Text(label!!) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isNumeric) KeyboardType.Number
                        else KeyboardType.Text
                    ),
                    // 当按下 Done 时触发保存（如果通过校验）
                    // 这里使用 keyboardActions 需要引入 accompanist 或 material -> 也可在 TextField 层外监听
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
                    // 校验
                    val validation = validator(text)
                    if (validation == null) {
                        // 合法：隐藏键盘并触发保存回调
                        focusManager.clearFocus()
                        hideKeyboard(context)
                        onConfirm(text)
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

/** 隐藏键盘的小工具 */
private fun hideKeyboard(context: android.content.Context) {
    val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
    val activity = context as? Activity
    val view = activity?.currentFocus
    if (view != null && imm != null) {
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
