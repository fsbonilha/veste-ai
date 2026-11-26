package com.vesteai.feature.lookbook

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
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
    onToggleFavorite: (LookCard) -> Unit,
    onReload: (LookCard) -> Unit,
    onRequestDelete: (LookCard) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDismissDeleteError: () -> Unit,
    onCancelJob: (PendingJob) -> Unit,
    onRetryJob: (PendingJob) -> Unit,
    onSaveJobToLookbook: (PendingJob) -> Unit,
    onDiscardJob: (PendingJob) -> Unit,
    onReloadJob: (PendingJob) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (state) {
            LookbookUiState.Loading -> LoadingState()

            LookbookUiState.Error -> ErrorState(
                message = stringResource(R.string.lookbook_load_failed),
                onRetry = onRetry,
            )

            is LookbookUiState.Content -> {
                if (state.pendingJobs.isNotEmpty()) {
                    PendingJobList(
                        jobs = state.pendingJobs,
                        imageFile = imageFile,
                        onCancel = onCancelJob,
                        onRetry = onRetryJob,
                        onSave = onSaveJobToLookbook,
                        onDiscard = onDiscardJob,
                        onReload = onReloadJob,
                    )
                }
                if (state.looks.isEmpty()) {
                    EmptyState(message = stringResource(R.string.lookbook_empty))
                } else {
                    LookGrid(
                        looks = state.looks,
                        imageFile = imageFile,
                        onToggleFavorite = onToggleFavorite,
                        onReload = onReload,
                        onRequestDelete = onRequestDelete,
                        modifier = Modifier.weight(1f),
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

/** Cards for in-progress and recently-completed try-on jobs. */
@Composable
private fun PendingJobList(
    jobs: List<PendingJob>,
    imageFile: (String) -> File,
    onCancel: (PendingJob) -> Unit,
    onRetry: (PendingJob) -> Unit,
    onSave: (PendingJob) -> Unit,
    onDiscard: (PendingJob) -> Unit,
    onReload: (PendingJob) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.lookbook_pending_section),
            style = MaterialTheme.typography.titleSmall,
        )
        jobs.forEach { job -> PendingJobCard(job, imageFile, onCancel, onRetry, onSave, onDiscard, onReload) }
    }
}

@Composable
private fun PendingJobCard(
    job: PendingJob,
    imageFile: (String) -> File,
    onCancel: (PendingJob) -> Unit,
    onRetry: (PendingJob) -> Unit,
    onSave: (PendingJob) -> Unit,
    onDiscard: (PendingJob) -> Unit,
    onReload: (PendingJob) -> Unit,
) {
    val isActive = !job.status.isTerminal
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    if (job.status is JobStatus.Queued) R.string.tryon_stage_queued
                    else if (job.status is JobStatus.Processing) R.string.tryon_stage_processing
                    else if (job.status is JobStatus.Completed) R.string.tryon_stage_completed
                    else R.string.tryon_stage_failed
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            val garmentCount = job.garmentIds.size
            Text(
                text = stringResource(R.string.lookbook_job_garments, garmentCount.toString()),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isActive) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onCancel(job) },
                        modifier = Modifier.accessibleTouchTarget(),
                    ) { Text(stringResource(R.string.action_cancel)) }
                }
            } else if (job.status is JobStatus.Completed) {
                val completed = job.status as JobStatus.Completed
                AsyncImage(
                    model = imageFile(completed.resultImagePath),
                    contentDescription = stringResource(R.string.tryon_result_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSave(job) },
                        modifier = Modifier.accessibleTouchTarget(),
                    ) { Text(stringResource(R.string.action_save_to_lookbook)) }
                    OutlinedButton(
                        onClick = { onDiscard(job) },
                        modifier = Modifier.accessibleTouchTarget(),
                    ) { Text(stringResource(R.string.action_discard)) }
                    OutlinedButton(
                        onClick = { onReload(job) },
                        modifier = Modifier.accessibleTouchTarget(),
                    ) {
                        Text(stringResource(R.string.action_reload_in_fitting_room))
                    }
                }
            } else if (job.status is JobStatus.Failed) {
                Text(
                    text = stringResource(R.string.lookbook_job_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = { onRetry(job) },
                    modifier = Modifier.accessibleTouchTarget(),
                ) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun LookGrid(
    looks: List<LookCard>,
    imageFile: (String) -> File,
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
            AsyncImage(
                model = imageFile(look.outfit.resultImagePath),
                contentDescription = stringResource(R.string.lookbook_saved_on, savedOn),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
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