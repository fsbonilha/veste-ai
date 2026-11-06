package com.vesteai.feature.fittingroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.core.error.userMessage
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.feature.wardrobe.labelRes
import java.io.File

@Composable
internal fun FittingRoomScreen(
    state: FittingRoomUiState,
    baselineFile: File?,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
    onTryOn: () -> Unit,
    onCancelTryOn: () -> Unit,
    onDismissTryOnError: () -> Unit,
    onRetry: () -> Unit,
    onSaveToLookbook: () -> Unit,
    onDismissSaveFailure: () -> Unit,
    onDismissShareFailure: () -> Unit,
    onShareResult: (String) -> Unit,
    onDiscardResult: () -> Unit,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingBox()
            state.loadFailed -> LoadFailureBox(onRetry = onRetry)
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    state = state,
                    baselineFile = baselineFile,
                    imageFile = imageFile,
                    onToggleGarment = onToggleGarment,
                    modifier = Modifier.weight(1f),
                )
                TryOnProgress(state = state, onCancel = onCancelTryOn)
                ActionBar(
                    state = state,
                    onOpenDrawer = { drawerOpen = true },
                    onTryOn = onTryOn,
                )
            }
        }

        val tryOnStage = state.tryOnStage
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (tryOnStage is TryOnStage.Failed) {
                ErrorBanner(message = userMessage(tryOnStage.error), onDismiss = onDismissTryOnError)
            }
            if (state.shareFailed) {
                ErrorBanner(
                    message = stringResource(R.string.tryon_share_unavailable),
                    onDismiss = onDismissShareFailure,
                )
            }
        }

        if (drawerOpen) {
            SelectionDrawer(
                state = state,
                imageFile = imageFile,
                onToggleGarment = onToggleGarment,
                onDismiss = { drawerOpen = false },
            )
        }

        val result = state.result
        if (result != null && state.tryOnStage == TryOnStage.Completed) {
            ResultSheet(
                result = result,
                imageFile = imageFile,
                onSaveToLookbook = onSaveToLookbook,
                onDismissSaveFailure = onDismissSaveFailure,
                onShare = { onShareResult(result.relativePath) },
                onDiscard = onDiscardResult,
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadFailureBox(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.fitting_room_load_failed),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/** Baseline plus semi-transparent selection chips. No garment composite happens here. */
@Composable
private fun Canvas(
    state: FittingRoomUiState,
    baselineFile: File?,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (baselineFile != null) {
            AsyncImage(
                model = baselineFile,
                contentDescription = stringResource(R.string.fitting_room_baseline_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.fitting_room_no_baseline),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (state.hasGarments && state.selection.selected.isNotEmpty()) {
            SelectionChips(
                garments = state.selection.selected.values.toList(),
                imageFile = imageFile,
                onToggleGarment = onToggleGarment,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }

        if (baselineFile != null && !state.hasGarments) {
            Text(
                text = stringResource(R.string.fitting_room_no_garments),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun SelectionChips(
    garments: List<Garment>,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(garments, key = { it.id }) { garment ->
            val label = stringResource(garment.category.labelRes())
            val removeLabel = stringResource(R.string.action_remove_selection, label)
            AssistChip(
                onClick = { onToggleGarment(garment) },
                label = { Text(label) },
                modifier = Modifier
                    .alpha(CHIP_ALPHA)
                    .semantics { contentDescription = removeLabel },
                leadingIcon = {
                    AsyncImage(
                        model = imageFile(garment.imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

/** Staged progress for a long-running job: text stages, elapsed timer and Cancel. */
@Composable
private fun TryOnProgress(state: FittingRoomUiState, onCancel: () -> Unit) {
    val stage = state.tryOnStage
    if (!stage.isInFlight) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(
                when (stage) {
                    TryOnStage.Queued -> R.string.tryon_stage_queued
                    else -> R.string.tryon_stage_processing
                },
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tryon_elapsed, "${state.elapsedSeconds} s"),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun ActionBar(
    state: FittingRoomUiState,
    onOpenDrawer: () -> Unit,
    onTryOn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onOpenDrawer, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.fitting_room_open_drawer), maxLines = 2)
        }
        Button(
            onClick = onTryOn,
            enabled = state.canTryOn,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.action_try_on), maxLines = 1)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionDrawer(
    state: FittingRoomUiState,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
    onDismiss: () -> Unit,
) {
    val tabs = GarmentCategory.entries
    // Open on something the user can actually pick, so a disabled tab is never the first thing seen.
    val initial = tabs.firstOrNull {
        state.isSupported(it) && state.garmentsByCategory[it].orEmpty().isNotEmpty()
    } ?: tabs.first { state.isSupported(it) }
    var activeTab by rememberSaveable { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(activeTab), edgePadding = 8.dp) {
                tabs.forEach { category ->
                    Tab(
                        selected = category == activeTab,
                        onClick = { activeTab = category },
                        text = { Text(stringResource(category.labelRes())) },
                    )
                }
            }
            HorizontalDivider()
            DrawerBody(
                category = activeTab,
                state = state,
                imageFile = imageFile,
                onToggleGarment = onToggleGarment,
            )
        }
    }
}

@Composable
private fun DrawerBody(
    category: GarmentCategory,
    state: FittingRoomUiState,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
) {
    val garments = state.garmentsByCategory[category].orEmpty()
    val supported = state.isSupported(category)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!supported) {
            Text(
                text = stringResource(
                    R.string.fitting_room_unsupported_category,
                    stringResource(category.labelRes()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (garments.isEmpty()) {
            Text(
                text = stringResource(R.string.fitting_room_drawer_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        garments.forEach { garment ->
            DrawerGarmentRow(
                garment = garment,
                imageFile = imageFile(garment.imagePath),
                selected = state.selection.isSelected(garment),
                enabled = supported,
                onClick = { onToggleGarment(garment) },
            )
        }
    }
}

@Composable
private fun DrawerGarmentRow(
    garment: Garment,
    imageFile: File,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = garment.displayName ?: stringResource(garment.category.labelRes())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageFile,
            contentDescription = stringResource(garment.category.labelRes()),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).alpha(if (enabled) 1f else DISABLED_ALPHA),
        )
        if (selected) {
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(label, maxLines = 1)
            }
        } else {
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(
    result: TryOnResultUiState,
    imageFile: (String) -> File,
    onSaveToLookbook: () -> Unit,
    onDismissSaveFailure: () -> Unit,
    onShare: () -> Unit,
    onDiscard: () -> Unit,
) {
    // The result is the point of the flow, so it opens fully rather than at the half detent.
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.tryon_stage_completed),
                style = MaterialTheme.typography.titleMedium,
            )
            AsyncImage(
                model = imageFile(result.relativePath),
                contentDescription = stringResource(R.string.tryon_result_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(RESULT_IMAGE_MAX_HEIGHT),
            )
            if (result.saveFailed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tryon_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissSaveFailure) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
            if (result.saved) {
                Text(
                    text = stringResource(R.string.tryon_saved),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveToLookbook,
                    enabled = !result.saving && !result.saved,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) { Text(stringResource(R.string.action_save_to_lookbook)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                    ) { Text(stringResource(R.string.action_share)) }
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        // Once saved the file belongs to the lookbook, so the action only closes.
                        Text(
                            stringResource(
                                if (result.saved) R.string.action_done else R.string.action_discard,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private const val CHIP_ALPHA = 0.75f
private const val DISABLED_ALPHA = 0.4f
private val RESULT_IMAGE_MAX_HEIGHT = 240.dp
