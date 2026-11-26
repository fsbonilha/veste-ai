package com.vesteai.data.vton.mock

import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonPollResult
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MockVtonEngine(
    private val dispatcher: CoroutineDispatcher,
    private val compositor: MockCompositor,
    private val renderDelay: Duration = DEFAULT_RENDER_DELAY,
    override val capabilities: VtonCapabilities = DEFAULT_CAPABILITIES,
) : VtonEngine {

    override val displayName: String = "Mock (offline)"

    /** Mock engine does not support async submit; only [render] works. */
    override suspend fun submit(request: VtonRequest): DataResult<String> =
        DataResult.Failure(VtonException(VtonError.UnsupportedCombination))

    /** Mock engine does not support async poll; only [render] works. */
    override suspend fun poll(jobId: String): DataResult<VtonPollResult?> =
        DataResult.Failure(VtonException(VtonError.UnsupportedCombination))

    override suspend fun render(request: VtonRequest): DataResult<VtonResult> = withContext(dispatcher) {
        if (!isSupported(request.garments.keys)) return@withContext failure(VtonError.UnsupportedCombination)
        delay(renderDelay)
        val image = compositor.composite(request.baselineImage, request.garments)
            ?: return@withContext failure(VtonError.InvalidImage)
        DataResult.Success(VtonResult(image))
    }

    private fun isSupported(categories: Set<GarmentCategory>): Boolean {
        val isOutfit = GarmentCategory.TOP in categories && GarmentCategory.BOTTOM in categories
        return categories.isNotEmpty() &&
            capabilities.supportedCategories.containsAll(categories) &&
            (!isOutfit || capabilities.supportsTopBottomOutfit)
    }

    private fun failure(error: VtonError) = DataResult.Failure(VtonException(error))

    companion object {
        val DEFAULT_RENDER_DELAY: Duration = 800.milliseconds

        val DEFAULT_CAPABILITIES = VtonCapabilities(
            supportedCategories = GarmentCategory.entries.toSet(),
            supportsTopBottomOutfit = true,
            maxParallelJobs = 1,
            requiredAspect = AspectRequirement.Unconstrained,
        )
    }
}