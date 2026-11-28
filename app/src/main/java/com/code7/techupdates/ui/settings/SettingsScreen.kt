package com.code7.techupdates.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.code7.techupdates.AppLinks
import com.code7.techupdates.R
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.ui.common.openInBrowser
import com.code7.techupdates.ui.theme.Code7Theme

private val ThemeMode.labelRes: Int
    get() =
        when (this) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        }

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                SettingsEvent.LearningReset -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.settings_reset_done))
                }
            }
        }
    }
    val actions =
        remember(viewModel, context) {
            SettingsActions(
                onBack = onBack,
                onSelectTheme = viewModel::selectTheme,
                onDynamicColorChange = viewModel::setDynamicColor,
                onRequestReset = viewModel::requestReset,
                onDismissReset = viewModel::dismissReset,
                onConfirmReset = viewModel::confirmReset,
                onOpenLink = context::openInBrowser,
            )
        }
    SettingsScreen(state, actions, snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.icon_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!state.isLoading) AppearanceSection(state, actions)
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_for_you))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reset_learning)) },
                supportingContent = { Text(stringResource(R.string.settings_reset_learning_summary)) },
                modifier = Modifier.clickable(onClick = actions.onRequestReset),
            )
            HorizontalDivider()
            AboutSection(state.versionLabel, actions.onOpenLink)
        }
    }
    if (state.confirmingReset) ResetDialog(actions)
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    SectionHeader(stringResource(R.string.settings_section_appearance))
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    SegmentedButton(
                        selected = mode == state.themeMode,
                        onClick = { actions.onSelectTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(mode.ordinal, ThemeMode.entries.size),
                        label = { Text(stringResource(mode.labelRes)) },
                    )
                }
            }
        },
    )
    val dynamicOn = state.dynamicColorSupported && state.dynamicColor
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
        supportingContent = {
            Text(
                stringResource(
                    if (state.dynamicColorSupported) {
                        R.string.settings_dynamic_color_summary
                    } else {
                        R.string.settings_dynamic_color_unsupported
                    },
                ),
            )
        },
        trailingContent = {
            Switch(
                checked = dynamicOn,
                onCheckedChange = null,
                enabled = state.dynamicColorSupported,
            )
        },
        modifier =
            Modifier.toggleable(
                value = dynamicOn,
                enabled = state.dynamicColorSupported,
                role = Role.Switch,
                onValueChange = actions.onDynamicColorChange,
            ),
    )
}

@Composable
private fun AboutSection(
    versionLabel: String,
    onOpenLink: (String) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_about))
    Text(
        stringResource(R.string.settings_attribution),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_source)) },
        supportingContent = { Text(stringResource(R.string.settings_source_summary)) },
        trailingContent = { Icon(painterResource(R.drawable.ic_open_external), contentDescription = null) },
        modifier = Modifier.clickable { onOpenLink(AppLinks.SOURCE_REPO) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_privacy)) },
        supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) },
        trailingContent = { Icon(painterResource(R.drawable.ic_open_external), contentDescription = null) },
        modifier = Modifier.clickable { onOpenLink(AppLinks.PRIVACY_POLICY) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_version)) },
        supportingContent = { Text(versionLabel) },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResetDialog(actions: SettingsActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissReset,
        title = { Text(stringResource(R.string.settings_reset_title)) },
        text = { Text(stringResource(R.string.settings_reset_message)) },
        confirmButton = {
            TextButton(onClick = actions.onConfirmReset) { Text(stringResource(R.string.settings_reset_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissReset) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private val PreviewState = SettingsUiState(isLoading = false, themeMode = ThemeMode.DARK, versionLabel = "v0.1.0 (1)")

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    Code7Theme(dynamicColor = false) { SettingsScreen(PreviewState, SettingsActions()) }
}

@Preview(name = "Unsupported dynamic color, light", showBackground = true)
@Preview(name = "Unsupported dynamic color, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenNoDynamicPreview() {
    Code7Theme(dynamicColor = false) {
        SettingsScreen(PreviewState.copy(dynamicColorSupported = false), SettingsActions())
    }
}

@Preview(name = "Reset dialog light", showBackground = true)
@Preview(name = "Reset dialog dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsResetDialogPreview() {
    Code7Theme(dynamicColor = false) { SettingsScreen(PreviewState.copy(confirmingReset = true), SettingsActions()) }
}
