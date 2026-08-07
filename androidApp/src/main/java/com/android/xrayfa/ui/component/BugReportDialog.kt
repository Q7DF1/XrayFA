package com.android.xrayfa.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    val canSubmit = title.isNotBlank() && description.isNotBlank()
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Hero header ──────────────────────────────────────────
                BugReportHeader()

                // ── Form body ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FieldBlock(
                        icon = Icons.Outlined.Title,
                        label = stringResource(id = R.string.bug_report_title_label),
                        required = true,
                        value = title,
                        limit = titleLimit
                    ) {
                        ReportTextField(
                            value = title,
                            onValueChange = { if (it.length <= titleLimit) title = it },
                            placeholder = stringResource(id = R.string.bug_report_title_label),
                            singleLine = true
                        )
                    }

                    FieldBlock(
                        icon = Icons.Outlined.Description,
                        label = stringResource(id = R.string.bug_report_desc_label),
                        required = true,
                        value = description,
                        limit = descriptionLimit
                    ) {
                        ReportTextField(
                            value = description,
                            onValueChange = { if (it.length <= descriptionLimit) description = it },
                            placeholder = stringResource(id = R.string.bug_report_desc_label),
                            minLines = 3
                        )
                    }

                    FieldBlock(
                        icon = Icons.Outlined.CheckCircleOutline,
                        label = stringResource(id = R.string.bug_report_expected_label),
                        required = false,
                        value = expectedBehavior,
                        limit = behaviorLimit
                    ) {
                        ReportTextField(
                            value = expectedBehavior,
                            onValueChange = { if (it.length <= behaviorLimit) expectedBehavior = it },
                            placeholder = stringResource(id = R.string.bug_report_expected_label),
                            minLines = 2
                        )
                    }

                    FieldBlock(
                        icon = Icons.Outlined.ErrorOutline,
                        label = stringResource(id = R.string.bug_report_actual_label),
                        required = false,
                        value = actualBehavior,
                        limit = behaviorLimit,
                        accent = MaterialTheme.colorScheme.error
                    ) {
                        ReportTextField(
                            value = actualBehavior,
                            onValueChange = { if (it.length <= behaviorLimit) actualBehavior = it },
                            placeholder = stringResource(id = R.string.bug_report_actual_label),
                            minLines = 2
                        )
                    }
                }

                // ── Action bar ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
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
                        enabled = canSubmit,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.bug_report_submit),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Header ───────────────────────── */

@Composable
private fun BugReportHeader() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.bug_report_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.bug_report_submit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/* ───────────────────────── Field block ───────────────────────── */

@Composable
private fun FieldBlock(
    icon: ImageVector,
    label: String,
    required: Boolean,
    value: String,
    limit: Int,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val nearLimit = value.length >= (limit * 0.9f).toInt()
    val overLimit = value.length >= limit
    val counterColor by animateColorAsState(
        targetValue = when {
            overLimit -> MaterialTheme.colorScheme.error
            nearLimit -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "counterColor"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (required) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "*",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${value.length}/$limit",
                style = MaterialTheme.typography.labelSmall,
                color = counterColor,
                fontWeight = if (overLimit) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        content()
    }
}

/* ───────────────────────── Text field ───────────────────────── */

@Composable
private fun ReportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    )
}
