package com.vesteai.feature.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.usecase.DeletionPlan
import com.vesteai.ui.common.EmptyState
import com.vesteai.ui.common.ErrorState
import com.vesteai.ui.common.LoadingState
import com.vesteai.ui.common.MinimumTouchTarget
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
            WardrobeUiState.Loading -> LoadingState()

            WardrobeUiState.Error -> ErrorState(
                message = stringResource(R.string.wardrobe_load_failed),
                onRetry = onRetry,
            )

            is WardrobeUiState.Content -> {
                CategoryFilterRow(selected = state.filter, onSelected = onFilterSelected)
                if (state.garments.isEmpty()) {
                    EmptyState(
                        message = stringResource(
                            if (state.filter != null) {
                                R.string.wardrobe_empty_filtered
                            } else {
                                R.string.wardrobe_empty
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GarmentGrid(
                        garments = state.garments,
                        imageFile = imageFile,
                        onToggleFavorite = onToggleFavorite,
                        onRequestDelete = onRequestDelete,
                        modifier = Modifier.weight(1f),
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
                modifier = Modifier.heightIn(min = MinimumTouchTarget),
            )
        }
        items(GarmentCategory.entries) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelected(category) },
                label = { Text(stringResource(category.labelRes())) },
                modifier = Modifier.heightIn(min = MinimumTouchTarget),
            )
        }
    }
}

@Composable
private fun GarmentGrid(
    garments: List<Garment>,
    imageFile: (String) -> File,
    onToggleFavorite: (Garment) -> Unit,
    onRequestDelete: (Garment) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier.fillMaxWidth(),
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
    val name = garment.displayName ?: stringResource(garment.category.labelRes())
    val favoriteLabel = stringResource(R.string.action_favorite_named, name)
    val unfavoriteLabel = stringResource(R.string.action_unfavorite_named, name)
    val deleteLabel = stringResource(R.string.action_delete_named, name)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = imageFile,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
        )
        Text(text = name, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // "Favorite" on its own repeats once per tile, so each action also names its
            // garment; the visible label stays short and the announced one is unambiguous.
            TextButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .heightIn(min = MinimumTouchTarget)
                    .semantics {
                        contentDescription = if (garment.isFavorite) unfavoriteLabel else favoriteLabel
                    },
            ) {
                Text(
                    if (garment.isFavorite) {
                        stringResource(R.string.action_unfavorite)
                    } else {
                        stringResource(R.string.action_favorite)
                    },
                )
            }
            TextButton(
                onClick = onRequestDelete,
                modifier = Modifier
                    .heightIn(min = MinimumTouchTarget)
                    .semantics { contentDescription = deleteLabel },
            ) {
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
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = MinimumTouchTarget),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = MinimumTouchTarget),
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun MessageSnackbar(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(16.dp),
        action = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = MinimumTouchTarget),
            ) { Text(stringResource(R.string.action_dismiss)) }
        },
    ) { Text(message) }
}

@Composable
private fun AddGarmentBar(
    onAddFromGallery: () -> Unit,
    onAddFromCamera: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Labels wrap to two lines instead of truncating, and the buttons grow with the text
        // rather than sitting at a fixed height, so neither label is clipped at a large font scale.
        Button(
            onClick = onAddFromGallery,
            modifier = Modifier.weight(1f).heightIn(min = MinimumTouchTarget),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.action_add_from_gallery), maxLines = 2)
        }
        OutlinedButton(
            onClick = onAddFromCamera,
            modifier = Modifier.weight(1f).heightIn(min = MinimumTouchTarget),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.action_add_from_camera), maxLines = 2)
        }
    }
}
