package com.vesteai.feature.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.usecase.DeletionPlan
import java.io.File

@Composable
internal fun WardrobeScreen(
    state: WardrobeUiState,
    captureError: ImportError?,
    imageFile: (String) -> File,
    onFilterSelected: (GarmentCategory?) -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (Garment) -> Unit,
    onRequestDelete: (Garment) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDismissDeleteError: () -> Unit,
    onDismissCaptureError: () -> Unit,
    onAddFromGallery: () -> Unit,
    onAddFromCamera: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (state) {
            WardrobeUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            WardrobeUiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.wardrobe_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            is WardrobeUiState.Content -> {
                CategoryFilterRow(selected = state.filter, onSelected = onFilterSelected)
                if (state.garments.isEmpty()) {
                    EmptyWardrobe(filtered = state.filter != null)
                } else {
                    GarmentGrid(
                        garments = state.garments,
                        imageFile = imageFile,
                        onToggleFavorite = onToggleFavorite,
                        onRequestDelete = onRequestDelete,
                    )
                }
                state.pendingDeletion?.let { pending ->
                    DeleteConfirmDialog(
                        plan = pending.plan,
                        onConfirm = onConfirmDelete,
                        onCancel = onCancelDelete,
                    )
                }
                if (state.deleteFailed) {
                    MessageSnackbar(
                        message = stringResource(R.string.garment_delete_failed),
                        onDismiss = onDismissDeleteError,
                    )
                }
            }
        }
        if (captureError != null) {
            MessageSnackbar(
                message = stringResource(
                    when (captureError) {
                        ImportError.TooSmall -> R.string.import_too_small
                        ImportError.Unreadable -> R.string.import_unreadable
                    },
                ),
                onDismiss = onDismissCaptureError,
            )
        }
        AddGarmentBar(onAddFromGallery = onAddFromGallery, onAddFromCamera = onAddFromCamera)
    }
}

@Composable
private fun CategoryFilterRow(
    selected: GarmentCategory?,
    onSelected: (GarmentCategory?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
        }
        items(GarmentCategory.entries) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelected(category) },
                label = { Text(stringResource(category.labelRes())) },
            )
        }
    }
}

@Composable
private fun EmptyWardrobe(filtered: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (filtered) R.string.wardrobe_empty_filtered else R.string.wardrobe_empty,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun GarmentGrid(
    garments: List<Garment>,
    imageFile: (String) -> File,
    onToggleFavorite: (Garment) -> Unit,
    onRequestDelete: (Garment) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(garments, key = { it.id }) { garment ->
            GarmentTile(
                garment = garment,
                imageFile = imageFile(garment.imagePath),
                onToggleFavorite = { onToggleFavorite(garment) },
                onRequestDelete = { onRequestDelete(garment) },
            )
        }
    }
}

@Composable
private fun GarmentTile(
    garment: Garment,
    imageFile: File,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val description = stringResource(garment.category.labelRes())
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = imageFile,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
        )
        Text(text = description, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToggleFavorite) {
                Text(
                    stringResource(
                        if (garment.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                    ),
                )
            }
            TextButton(onClick = onRequestDelete) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    plan: DeletionPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val (title, body) = when (plan) {
        DeletionPlan.Standalone ->
            stringResource(R.string.delete_garment_title) to stringResource(R.string.delete_garment_body)
        is DeletionPlan.CascadesLookbooks ->
            stringResource(R.string.delete_garment_cascade_title) to
                pluralStringResource(
                    R.plurals.deleting_garment_cascades_lookbooks,
                    plan.lookbookCount,
                    plan.lookbookCount,
                )
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun MessageSnackbar(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    ) { Text(message) }
}

@Composable
private fun AddGarmentBar(
    onAddFromGallery: () -> Unit,
    onAddFromCamera: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onAddFromGallery) { Text(stringResource(R.string.action_add_from_gallery)) }
        OutlinedButton(onClick = onAddFromCamera) {
            Text(stringResource(R.string.action_add_from_camera))
        }
    }
}
