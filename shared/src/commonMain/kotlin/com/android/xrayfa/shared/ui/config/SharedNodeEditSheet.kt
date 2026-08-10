package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.protocol.protocolsPrefix
import com.android.xrayfa.shared.ui.widgets.SharedModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedNodeEditSheet(
    labels: ConfigUiLabels,
    showError: Boolean,
    onDismiss: () -> Unit,
    onSave: (remark: String, link: String) -> Unit,
    sheetTitle: String = labels.editNodeTitle,
    initialRemark: String = "",
    initialLink: String = "",
) {
    var remark by remember(sheetTitle, initialRemark, initialLink) { mutableStateOf(initialRemark) }
    var link by remember(sheetTitle, initialRemark, initialLink) { mutableStateOf(initialLink) }
    var linkInvalid by remember(sheetTitle, initialRemark, initialLink) { mutableStateOf(false) }

    fun validateLink(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        val prefix = trimmed.substringBefore("://").lowercase()
        return protocolsPrefix.contains(prefix)
    }

    SharedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = sheetTitle,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text(labels.nodeRemarkLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    linkInvalid = !validateLink(it)
                },
                label = { Text(labels.nodeUrlLabel) },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                isError = linkInvalid || showError,
                supportingText =
                    if (linkInvalid || showError) {
                        { Text(labels.editNodeFailed) }
                    } else {
                        null
                    },
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(labels.cancelLabel)
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        linkInvalid = !validateLink(link)
                        if (linkInvalid) return@Button
                        onSave(remark.trim(), link.trim())
                    },
                    enabled = !linkInvalid,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(labels.saveLabel)
                }
            }
        }
    }
}
