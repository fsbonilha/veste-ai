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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vesteai.R
import com.vesteai.core.error.userMessage
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
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
internal fun FittingRoomScreen(
    state: FittingRoomUiState,
    baselineFile: File?,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
    onSelectTab: (FittingRoomTab) -> Unit,
    onTryOn: () -> Unit,
    onCancelTryOn: () -> Unit,
    onDismissTryOnError: () -> Unit,
    onDismissSavedConfirmation: () -> Unit,
    onRetry: () -> Unit,
    onCancelJob: (PendingJob) -> Unit,
    onRetryJob: (PendingJob) -> Unit,
    onRetrySaveJob: (PendingJob) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingState()
            state.loadFailed -> ErrorState(
                message = stringResource(R.string.fitting_room_load_failed),
                onRetry = onRetry,
            )
            else -> {
                FittingRoomTabs(state = state, onSelectTab = onSelectTab)
                Box(modifier = Modifier.weight(1f)) {
                    when (state.tab) {
                        FittingRoomTab.TryOn -> TryOnPane(
                            state = state,
                            baselineFile = baselineFile,
                            imageFile = imageFile,
                            onToggleGarment = onToggleGarment,
                            onTryOn = onTryOn,
                            onCancelTryOn = onCancelTryOn,
                            onDismissTryOnError = onDismissTryOnError,
                            onOpenOngoing = { onSelectTab(FittingRoomTab.Ongoing) },
                        )
                        FittingRoomTab.Ongoing -> OngoingPane(
                            state = state,
                            onCancelJob = onCancelJob,
                            onRetryJob = onRetryJob,
                            onRetrySaveJob = onRetrySaveJob,
                        )
                    }
                }
                if (state.savedConfirmation) {
                    SavedBanner(onDismiss = onDismissSavedConfirmation)
                }
            }
        }
    }
}

/** Segmented control: Try On, with an "Ongoing" tab that badges the active-job count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FittingRoomTabs(state: FittingRoomUiState, onSelectTab: (FittingRoomTab) -> Unit) {
    PrimaryScrollableTabRow(
        selectedTabIndex = if (state.tab == FittingRoomTab.TryOn) 0 else 1,
        edgePadding = 8.dp,
    ) {
        Tab(
            selected = state.tab == FittingRoomTab.TryOn,
            onClick = { onSelectTab(FittingRoomTab.TryOn) },
            text = { Text(stringResource(R.string.fitting_room_tab_canvas)) },
        )
        Tab(
            selected = state.tab == FittingRoomTab.Ongoing,
            onClick = { onSelectTab(FittingRoomTab.Ongoing) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.fitting_room_tab_ongoing))
                    if (state.activeJobCount > 0) {
                        Badge { Text(state.activeJobCount.toString()) }
                    }
                }
            },
        )
    }
}

@Composable
private fun TryOnPane(
    state: FittingRoomUiState,
    baselineFile: File?,
    imageFile: (String) -> File,
    onToggleGarment: (Garment) -> Unit,
    onTryOn: () -> Unit,
    onCancelTryOn: () -> Unit,
    onDismissTryOnError: () -> Unit,
    onOpenOngoing: () -> Unit,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Canvas(
                state = state,
                baselineFile = baselineFile,
                imageFile = imageFile,
                onToggleGarment = onToggleGarment,
                modifier = Modifier.weight(1f),
            )
            TryOnProgress(state = state, onCancel = onCancelTryOn, onOpenOngoing = onOpenOngoing)
            ActionBar(
                state = state,
                onOpenDrawer = { drawerOpen = true },
                onTryOn = onTryOn,
            )
        }

        val tryOnStage = state.tryOnStage
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (tryOnStage is TryOnStage.Failed) {
                ErrorBanner(message = userMessage(tryOnStage.error), onDismiss = onDismissTryOnError)
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
    }
}

@Composable
private fun OngoingPane(
    state: FittingRoomUiState,
    onCancelJob: (PendingJob) -> Unit,
    onRetryJob: (PendingJob) -> Unit,
    onRetrySaveJob: (PendingJob) -> Unit,
) {
    if (state.ongoingJobs.isEmpty()) {
        EmptyState(message = stringResource(R.string.fitting_room_ongoing_empty))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.ongoingJobs.forEach { job ->
            OngoingJobCard(
                job = job,
                onCancel = { onCancelJob(job) },
                onRetry = { onRetryJob(job) },
                onRetrySave = { onRetrySaveJob(job) },
            )
        }
    }
}

@Composable
private fun OngoingJobCard(
    job: PendingJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
) {
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    when (job.status) {
                        is JobStatus.Queued -> R.string.tryon_stage_queued
                        is JobStatus.Processing -> R.string.tryon_stage_processing
                        is JobStatus.Completed -> R.string.tryon_stage_completed
                        is JobStatus.Failed -> R.string.tryon_stage_failed
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.fitting_room_job_garments, job.garmentIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                job.isActive -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel, modifier = Modifier.accessibleTouchTarget()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                job.status is JobStatus.Failed -> {
                    Text(
                        text = stringResource(R.string.fitting_room_job_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onRetry, modifier = Modifier.accessibleTouchTarget()) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                job.status is JobStatus.Completed -> {
                    // Reached only when the lookbook write failed after a successful render.
                    Text(
                        text = stringResource(R.string.fitting_room_job_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onRetrySave, modifier = Modifier.accessibleTouchTarget()) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
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
                // The chip is the control, so it is announced as "Remove <garment> from the
                // selection"; the swatch beside the label is decorative and stays unlabelled.
                modifier = Modifier
                    .alpha(CHIP_ALPHA)
                    .heightIn(min = MinimumTouchTarget)
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

/** Staged progress for a long-running job: text stages, elapsed timer, Cancel and a link to Ongoing. */
@Composable
private fun TryOnProgress(state: FittingRoomUiState, onCancel: () -> Unit, onOpenOngoing: () -> Unit) {
    val stage = state.tryOnStage
    if (!stage.isInFlight) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stageLabel(stage, state),
            style = MaterialTheme.typography.titleSmall,
        )
        state.queue.userMessage?.takeIf { it.isNotBlank() }?.let { providerCopy ->
            // The provider knows the queue depth; its copy beats anything invented here (plan §7).
            Text(text = providerCopy, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = elapsedLabel(state),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // A job keeps running if the user leaves; the link says so without a second button.
                TextButton(onClick = onOpenOngoing, modifier = Modifier.accessibleTouchTarget()) {
                    Text(stringResource(R.string.fitting_room_view_ongoing))
                }
                TextButton(onClick = onCancel, modifier = Modifier.accessibleTouchTarget()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun stageLabel(stage: TryOnStage, state: FittingRoomUiState): String = when (stage) {
    TryOnStage.Queued -> stringResource(R.string.tryon_stage_queued)
    else -> {
        val position = state.queue.queuePosition
        if (position != null && position > 0) {
            stringResource(R.string.tryon_stage_queue_position, position)
        } else {
            stringResource(R.string.tryon_stage_processing)
        }
    }
}

/** Elapsed time plus the provider's ETA when it gives one; the timer is always shown. */
@Composable
private fun elapsedLabel(state: FittingRoomUiState): String {
    val elapsed = stringResource(R.string.tryon_elapsed, "${state.elapsedSeconds} s")
    val etaSeconds = state.queue.estimatedWaitSeconds
    return if (etaSeconds == null || etaSeconds <= 0) {
        elapsed
    } else {
        stringResource(R.string.tryon_elapsed_with_eta, elapsed, formatMinutes(etaSeconds))
    }
}

/** Coarse on purpose: the provider's estimate is minutes-level, so seconds would imply precision. */
private fun formatMinutes(seconds: Long): String = "${(seconds + 59) / 60} min"

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
        // The labels wrap to two lines and the bar is not given a fixed height, so neither action
        // is clipped away at a large font scale.
        OutlinedButton(
            onClick = onOpenDrawer,
            modifier = Modifier.weight(1f).heightIn(min = MinimumTouchTarget),
        ) {
            Text(stringResource(R.string.fitting_room_open_drawer), maxLines = 2)
        }
        Button(
            onClick = onTryOn,
            enabled = state.canTryOn,
            modifier = Modifier.weight(1f).heightIn(min = MinimumTouchTarget),
        ) {
            Text(stringResource(R.string.action_try_on), maxLines = 2)
        }
    }
}

@Composable
private fun SavedBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tryon_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.accessibleTouchTarget()) {
            Text(stringResource(R.string.action_dismiss))
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
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.accessibleTouchTarget(),
        ) { Text(stringResource(R.string.action_dismiss)) }
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
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).alpha(if (enabled) 1f else DISABLED_ALPHA),
        )
        if (selected) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = MinimumTouchTarget),
            ) {
                Text(label, maxLines = 2)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = MinimumTouchTarget),
            ) {
                Text(label, maxLines = 2)
            }
        }
    }
}

private const val CHIP_ALPHA = 0.75f
private const val DISABLED_ALPHA = 0.4f
