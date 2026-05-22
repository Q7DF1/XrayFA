package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.R
import com.android.xrayfa.model.BugReportData

@Composable
fun BugReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (BugReportData) -> Unit
) {
    val titleLimit = 100
    val descriptionLimit = 500
    val behaviorLimit = 300

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expectedBehavior by remember { mutableStateOf("") }
    var actualBehavior by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.bug_report_header),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactTextField(
                    value = title,
                    onValueChange = { if (it.length <= titleLimit) title = it },
                    label = stringResource(id = R.string.bug_report_title_label),
                    limit = titleLimit,
                    singleLine = true,
                    icon = Icons.Outlined.Title
                )

                CompactTextField(
                    value = description,
                    onValueChange = { if (it.length <= descriptionLimit) description = it },
                    label = stringResource(id = R.string.bug_report_desc_label),
                    limit = descriptionLimit,
                    minLines = 3,
                    icon = Icons.Outlined.Description
                )

                CompactTextField(
                    value = expectedBehavior,
                    onValueChange = { if (it.length <= behaviorLimit) expectedBehavior = it },
                    label = stringResource(id = R.string.bug_report_expected_label),
                    limit = behaviorLimit,
                    minLines = 2,
                    icon = Icons.Outlined.CheckCircleOutline
                )

                CompactTextField(
                    value = actualBehavior,
                    onValueChange = { if (it.length <= behaviorLimit) actualBehavior = it },
                    label = stringResource(id = R.string.bug_report_actual_label),
                    limit = behaviorLimit,
                    minLines = 2,
                    icon = Icons.Outlined.ErrorOutline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        BugReportData(
                            title = title,
                            description = description,
                            expectedBehavior = expectedBehavior,
                            actualBehavior = actualBehavior
                        )
                    )
                },
                enabled = title.isNotBlank() && description.isNotBlank()
            ) {
                Text(
                    text = stringResource(id = R.string.bug_report_submit),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    limit: Int,
    singleLine: Boolean = false,
    minLines: Int = 1,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        },
        supportingText = {
            Text(
                text = "${value.length} / $limit",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = if (value.length >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
    )
}
