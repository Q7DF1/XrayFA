package com.android.xrayfa.shared.ui.config

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.shared.config.*
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MuxEditorFields(form: NodeEditForm, onChange: (NodeEditForm) -> Unit, issue: String?) {
    val supported = form.supportsMux()
    if (!supported && form.mux == null) return

    val required = form.network == "xdrive"
    val mux = form.mux ?: if (required) NodeMuxForm() else null
    val unconfigured = stringResource(Res.string.mux_unconfigured)
    val enabled = stringResource(Res.string.mux_enabled)
    SharedEditDropdownField(
        value = if (mux == null) unconfigured else enabled,
        onValueChange = { onChange(form.copy(mux = if (it == enabled) mux ?: NodeMuxForm() else null)) },
        label = stringResource(Res.string.mux_title),
        options = when {
            required -> listOf(enabled)
            !supported -> listOf(enabled, unconfigured)
            else -> listOf(unconfigured, enabled)
        },
    )
    if (!supported) {
        if (form.network == "xhttp") MuxHint(stringResource(Res.string.mux_xhttp_hint))
        return
    }
    if (mux == null) return

    when {
        required -> MuxHint(stringResource(Res.string.mux_required_hint))
        form.selectedProtocol in setOf(Protocol.TROJAN, Protocol.SHADOWSOCKS, Protocol.SOCKS, Protocol.HTTP) ->
            MuxHint(stringResource(Res.string.mux_server_hint))
    }
    if (form.visionMux()) {
        MuxHint(stringResource(Res.string.mux_vision_hint))
    } else {
        SharedEditTextField(
            mux.concurrency,
            { onChange(form.copy(mux = mux.copy(concurrency = it))) },
            stringResource(Res.string.mux_tcp),
            keyboardType = KeyboardType.Ascii,
            isError = issue == "mux.concurrency",
        )
    }
    SharedEditTextField(
        mux.xudpConcurrency,
        { onChange(form.copy(mux = mux.copy(xudpConcurrency = it))) },
        stringResource(Res.string.mux_udp),
        keyboardType = KeyboardType.Ascii,
        isError = issue == "mux.xudpConcurrency",
    )
    val policies = linkedMapOf(
        "reject" to stringResource(Res.string.mux_udp443_reject),
        "allow" to stringResource(Res.string.mux_udp443_allow),
        "skip" to stringResource(Res.string.mux_udp443_skip),
    ).filterKeys { !required || it != "skip" }
    SharedEditDropdownField(
        value = policies[mux.udp443] ?: mux.udp443,
        onValueChange = { selected ->
            onChange(form.copy(mux = mux.copy(udp443 = policies.entries.first { it.value == selected }.key)))
        },
        label = stringResource(Res.string.mux_udp443),
        options = policies.values.toList(),
    )
    if (!required) MuxHint(stringResource(Res.string.mux_values_hint))
}

@Composable
private fun MuxHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
