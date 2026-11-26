package com.vesteai.feature.lookbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.feature.wardrobe.labelRes
import com.vesteai.ui.common.EmptyState
import com.vesteai.ui.common.ErrorState
import com.vesteai.ui.common.LoadingState
import com.vesteai.ui.common.MinimumTouchTarget
import com.vesteai.ui.common.accessibleTouchTarget
import java.io.File

@Composable
internal fun LookbookScreen(
    state: LookbookUiState,
    imageFile: (String) -> File,
    onRetry: () -> Unit,
    onOpenLook: (LookCard) -> Unit,
    onCloseLook: () -> Unit,
    onShareLook: (LookCard) -> Unit,
    onToggleFavorite: (LookCard) -> Unit,
    onReload: (LookCard) -> Unit,
    onRequestDelete: (LookCard) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDismissDeleteError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (state) {
            LookbookUiState.Loading -> LoadingState()

            LookbookUiState.Error -> ErrorState(
                message = stringResource(R.string.lookbook_load_failed),
                onRetry = onRetry,
            )

            is LookbookUiState.Content -> {
                if (state.looks.isEmpty()) {
                    EmptyState(message = stringResource(R.string.lookbook_empty))
                } else {
                    LookGrid(
                        looks = state.looks,
                        imageFile = imageFile,
                        onOpenLook = onOpenLook,
                        onToggleFavorite = onToggleFavorite,
                        onReload = onReload,
                        onRequestDelete = onRequestDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.viewing?.let { viewing ->
                    LookViewer(
                        look = viewing,
                        imageFile = imageFile,
                        onDismiss = onCloseLook,
                        onShare = { onShareLook(viewing) },
                        onToggleFavorite = { onToggleFavorite(viewing) },
                        onReload = { onReload(viewing) },
                        onDelete = { onRequestDelete(viewing) },
                    )
                }
                state.pendingDeletion?.let {
                    DeleteLookDialog(onConfirm = onConfirmDelete, onCancel = onCancelDelete)
                }
                if (state.deleteFailed) {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(
                                onClick = onDismissDeleteError,
                                modifier = Modifier.heightIn(min = MinimumTouchTarget),
                            ) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        },
                    ) { Text(stringResource(R.string.lookbook_delete_failed)) }
                }
            }
        }
    }
}

@Composable
private fun LookGrid(
    looks: List<LookCard>,
    imageFile: (String) -> File,
    onOpenLook: (LookCard) -> Unit,
    onToggleFavorite: (LookCard) -> Unit,
    onReload: (LookCard) -> Unit,
    onRequestDelete: (LookCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(looks, key = { it.outfit.id }) { look ->
            LookTile(
                look = look,
                imageFile = imageFile,
                onOpen = { onOpenLook(look) },
                onToggleFavorite = { onToggleFavorite(look) },
                onReload = { onReload(look) },
                onRequestDelete = { onRequestDelete(look) },
            )
        }
    }
}

@Composable
private fun LookTile(
    look: LookCard,
    imageFile: (String) -> File,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReload: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val savedOn = look.createdLabel
    val favoriteLabel = stringResource(R.string.action_favorite_look_named, savedOn)
    val unfavoriteLabel = stringResource(R.string.action_unfavorite_look_named, savedOn)
    val deleteLabel = stringResource(R.string.action_delete_look_named, savedOn)
    val reloadLabel = stringResource(R.string.action_reload_look_named, savedOn)
    Card {
        Column {
            // The image itself opens the look, like a photo in a gallery. The tap area is the whole
            // picture, not a small target, and the date stays its description so cards are
            // tellable apart.
            AsyncImage(
                model = imageFile(look.outfit.resultImagePath),
                contentDescription = stringResource(R.string.lookbook_saved_on, savedOn),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clickable(onClick = onOpen),
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.lookbook_saved_on, savedOn),
                    style = MaterialTheme.typography.labelLarge,
                )
                GarmentThumbnailStrip(garments = look.outfit.garments, imageFile = imageFile)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .heightIn(min = MinimumTouchTarget)
                            .semantics {
                                contentDescription = if (look.outfit.isFavorite) {
                                    unfavoriteLabel
                                } else {
                                    favoriteLabel
                                }
                            },
                    ) {
                        Text(
                            stringResource(
                                if (look.outfit.isFavorite) {
                                    R.string.action_unfavorite
                                } else {
                                    R.string.action_favorite
                                },
                            ),
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
                OutlinedButton(
                    onClick = onReload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinimumTouchTarget)
                        .semantics { contentDescription = reloadLabel },
                ) {
                    Text(stringResource(R.string.action_reload_in_fitting_room))
                }
            }
        }
    }
}

/** Full-screen, zoomable view of one saved look, opened from a tile. */
@Composable
private fun LookViewer(
    look: LookCard,
    imageFile: (String) -> File,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReload: () -> Unit,
    onDelete: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        // Clamp to [1x, 5x] so the photo can never shrink below fit or zoom away forever.
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = imageFile(look.outfit.resultImagePath),
                contentDescription = stringResource(R.string.tryon_result_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .transformable(transformState),
            )

            val closeLabel = stringResource(R.string.lookbook_viewer_close)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .accessibleTouchTarget()
                    .semantics { contentDescription = closeLabel },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    tint = Color.White,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = VIEWER_SCRIM_ALPHA))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onShare, modifier = Modifier.accessibleTouchTarget()) {
                    Text(stringResource(R.string.action_share), color = Color.White)
                }
                TextButton(onClick = onToggleFavorite, modifier = Modifier.accessibleTouchTarget()) {
                    Text(
                        text = stringResource(
                            if (look.outfit.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                        ),
                        color = Color.White,
                    )
                }
                TextButton(onClick = onReload, modifier = Modifier.accessibleTouchTarget()) {
                    Text(stringResource(R.string.action_reload_in_fitting_room), color = Color.White)
                }
                TextButton(onClick = onDelete, modifier = Modifier.accessibleTouchTarget()) {
                    Text(stringResource(R.string.action_delete), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GarmentThumbnailStrip(garments: List<Garment>, imageFile: (String) -> File) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(garments, key = { it.id }) { garment ->
            AsyncImage(
                model = imageFile(garment.imagePath),
                contentDescription = garment.displayName
                    ?: stringResource(garment.category.labelRes()),
                contentScale = ContentScale.Crop,
                modifier = Modifier.height(48.dp).aspectRatio(3f / 4f),
            )
        }
    }
}

@Composable
private fun DeleteLookDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.delete_lookbook_title)) },
        text = { Text(stringResource(R.string.delete_lookbook_body)) },
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

private const val VIEWER_SCRIM_ALPHA = 0.6f
