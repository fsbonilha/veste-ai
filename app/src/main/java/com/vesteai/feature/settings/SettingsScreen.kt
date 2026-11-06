package com.vesteai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vesteai.R
import com.vesteai.core.credentials.CredentialFormatError
import com.vesteai.core.credentials.CredentialState

/**
 * Settings (plan §7): re-take baseline, BYOK key entry with a masked read-back, engine display,
 * base URL, clear data, and the privacy copy that has to sit next to them.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onRetakeBaseline: () -> Unit,
    onRequestClearData: () -> Unit,
    onConfirmClearData: () -> Unit,
    onCancelClearData: () -> Unit,
    onDismissClearDataResult: () -> Unit,
    onDismissKeyMessage: () -> Unit,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        EngineSection(state)
        HorizontalDivider()
        BaselineSection(onRetakeBaseline)
        HorizontalDivider()
        KeySection(
            state = state,
            onApiKeyChanged = onApiKeyChanged,
            onSaveKey = onSaveKey,
            onRemoveKey = onRemoveKey,
            onDismissKeyMessage = onDismissKeyMessage,
        )
        HorizontalDivider()
        BaseUrlSection(state, onBaseUrlChanged)
        HorizontalDivider()
        PrivacySection()
        HorizontalDivider()
        ClearDataSection(state, onRequestClearData, onDismissClearDataResult)
    }

    if (state.clearData == ClearDataState.Confirming || state.clearData == ClearDataState.Clearing) {
        AlertDialog(
            onDismissRequest = onCancelClearData,
            title = { Text(stringResource(R.string.settings_clear_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_data_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmClearData,
                    enabled = state.clearData != ClearDataState.Clearing,
                ) { Text(stringResource(R.string.settings_clear_data_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelClearData) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EngineSection(state: SettingsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.settings_engine_label))
        Text(text = state.engineName, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(
                if (state.credential is CredentialState.Present) {
                    R.string.settings_engine_remote_hint
                } else {
                    R.string.settings_engine_mock_hint
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BaselineSection(onRetakeBaseline: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.settings_baseline_title))
        Text(
            text = stringResource(R.string.settings_baseline_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onRetakeBaseline) {
            Text(stringResource(R.string.settings_baseline_retake))
        }
    }
}

@Composable
private fun KeySection(
    state: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onDismissKeyMessage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.settings_key_title))
        Text(
            text = stringResource(R.string.settings_key_body),
            style = MaterialTheme.typography.bodyMedium,
        )

        val masked = state.maskedKey
        Text(
            text = when {
                masked != null -> stringResource(R.string.settings_key_stored, masked)
                state.needsReentry -> stringResource(R.string.settings_key_error_reentry)
                else -> stringResource(R.string.settings_key_absent)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.needsReentry) {
                MaterialTheme.colorScheme.error
            } else {
                LocalContentColor.current
            },
        )

        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = onApiKeyChanged,
            label = { Text(stringResource(R.string.settings_key_field_label)) },
            singleLine = true,
            enabled = !state.saving,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.settings_key_format_hint),
            style = MaterialTheme.typography.bodySmall,
        )

        state.keyMessage?.let { message ->
            KeyMessageText(message)
            TextButton(onClick = onDismissKeyMessage) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
        if (state.saving) {
            Text(
                text = stringResource(R.string.settings_key_saving),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSaveKey,
                enabled = state.canSave,
            ) { Text(stringResource(R.string.settings_key_save)) }
            if (state.maskedKey != null || state.needsReentry) {
                OutlinedButton(onClick = onRemoveKey) {
                    Text(stringResource(R.string.settings_key_remove))
                }
            }
        }
    }
}

@Composable
private fun KeyMessageText(message: KeyMessage) {
    val text = when (message) {
        is KeyMessage.RejectedFormat -> stringResource(
            when (message.error) {
                CredentialFormatError.BLANK -> R.string.settings_key_error_blank
                CredentialFormatError.MISSING_PREFIX -> R.string.settings_key_error_prefix
            },
        )
        KeyMessage.SaveFailed -> stringResource(R.string.settings_key_error_save)
        KeyMessage.StorageUnavailable -> stringResource(R.string.settings_key_error_storage)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun BaseUrlSection(
    state: SettingsUiState,
    onBaseUrlChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.settings_base_url_title))
        OutlinedTextField(
            value = state.baseUrlInput,
            onValueChange = onBaseUrlChanged,
            label = { Text(stringResource(R.string.settings_base_url_field_label)) },
            singleLine = true,
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_base_url_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PrivacySection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.settings_privacy_title))
        PrivacyLine(R.string.settings_privacy_own_key)
        PrivacyLine(R.string.settings_privacy_photos_leave)
        PrivacyLine(R.string.settings_privacy_local)
        PrivacyLine(R.string.settings_privacy_key_storage)
    }
}

@Composable
private fun PrivacyLine(resId: Int) {
    Text(text = stringResource(resId), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ClearDataSection(
    state: SettingsUiState,
    onRequestClearData: () -> Unit,
    onDismissResult: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.settings_clear_data_title))
        Text(
            text = stringResource(R.string.settings_clear_data_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        when (state.clearData) {
            ClearDataState.Clearing -> Text(
                text = stringResource(R.string.settings_clear_data_clearing),
                style = MaterialTheme.typography.bodyMedium,
            )
            ClearDataState.Cleared -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_data_done),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDismissResult) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
            ClearDataState.Failed -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_data_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismissResult) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
            ClearDataState.Idle, ClearDataState.Confirming -> Unit
        }
        OutlinedButton(
            onClick = onRequestClearData,
            enabled = state.clearData !is ClearDataState.Clearing,
        ) { Text(stringResource(R.string.settings_clear_data_action)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}