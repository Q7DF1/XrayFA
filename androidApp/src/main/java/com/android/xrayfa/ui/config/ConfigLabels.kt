package com.android.xrayfa.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.xrayfa.R
import com.android.xrayfa.shared.navigation.ConfigFilterLabels
import com.android.xrayfa.shared.ui.config.ConfigUiLabels
import com.android.xrayfa.ui.navigation.Config

@Composable
fun rememberConfigFilterLabels(): ConfigFilterLabels =
    ConfigFilterLabels(
        manualLabel = stringResource(R.string.import_manually),
        allLabel = stringResource(R.string.config_filter_all),
        favoriteLabel = stringResource(R.string.config_filter_favorite),
    )

@Composable
fun rememberConfigUiLabels(): ConfigUiLabels =
    ConfigUiLabels(
        title = stringResource(Config.title),
        manualFilterLabel = stringResource(R.string.import_manually),
        allFilterLabel = stringResource(R.string.config_filter_all),
        favoriteFilterLabel = stringResource(R.string.config_filter_favorite),
        emptyTitle = stringResource(R.string.no_configuration),
        emptyHint = stringResource(R.string.no_configuration_hint),
        createConfigLabel = stringResource(R.string.create_a_config),
        createNodeTitle = stringResource(R.string.config_create_node),
        unknownProtocolLabel = stringResource(R.string.unknown),
        timeoutLabel = stringResource(R.string.timeout),
        testingLabel = stringResource(R.string.config_testing),
        addToFavoritesLabel = stringResource(R.string.add_to_favorites),
        removeFromFavoritesLabel = stringResource(R.string.remove_from_favorites),
        testDelayLabel = stringResource(R.string.test_url),
        shareLabel = stringResource(R.string.clipboard_export),
        editLabel = stringResource(R.string.edit),
        deleteLabel = stringResource(R.string.delete),
        editNodeTitle = stringResource(R.string.config_edit_node),
        nodeRemarkLabel = stringResource(R.string.nick_name),
        nodeUrlLabel = stringResource(R.string.config_node_url),
        saveLabel = stringResource(R.string.save),
        cancelLabel = stringResource(R.string.cancel),
        editNodeFailed = stringResource(R.string.config_edit_node_failed),
        deleteNodeTitle = stringResource(R.string.config_delete_node_title),
        deleteNodeConfirm = stringResource(R.string.config_delete_node_confirm),
    )
