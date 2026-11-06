package com.vesteai.feature.wardrobe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.domain.model.GarmentCategory
import java.io.File

@Composable
fun GarmentDraftRoute(
    onFinished: () -> Unit,
    viewModel: GarmentDraftViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onFinished() }
    }

    val onBack = {
        if (state.step == DraftStep.TAGGING) viewModel.backToCropHint() else viewModel.discard()
    }
    BackHandler(onBack = onBack)

    GarmentDraftScreen(
        state = state,
        imageFile = viewModel.imageFile(),
        onContinue = viewModel::continueToTagging,
        onBack = onBack,
        onSelectCategory = viewModel::selectCategory,
        onSave = viewModel::save,
    )
}

@Composable
internal fun GarmentDraftScreen(
    state: DraftUiState,
    imageFile: File,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSelectCategory: (GarmentCategory) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = imageFile,
            contentDescription = stringResource(R.string.draft_image_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
        )
        when (state.step) {
            DraftStep.CROP_HINT -> {
                Text(
                    text = stringResource(R.string.crop_hint_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_continue))
                }
            }
            DraftStep.TAGGING -> {
                Text(
                    text = stringResource(R.string.tagging_prompt),
                    style = MaterialTheme.typography.titleMedium,
                )
                GarmentCategory.entries.forEach { category ->
                    val label = stringResource(category.labelRes())
                    if (state.category == category) {
                        Button(onClick = { onSelectCategory(category) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelectCategory(category) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label)
                        }
                    }
                }
                if (state.saveFailed) {
                    Text(
                        text = stringResource(R.string.garment_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = state.category != null && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}
