package com.vesteai.data.vton.mock

import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonException
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockVtonEngineTest {

    private val baseline = byteArrayOf(1, 2, 3)
    private val topBytes = byteArrayOf(10)
    private val bottomBytes = byteArrayOf(20)

    /** Deterministic stand-in for the Canvas compositor: baseline followed by garments in category order. */
    private val concatCompositor = MockCompositor { base, garments ->
        GarmentCategory.entries.mapNotNull { garments[it] }.fold(base) { acc, bytes -> acc + bytes }
    }

    private fun successImage(result: DataResult<VtonResult>): ByteArray =
        (result as DataResult.Success).value.image

    private fun failureError(result: DataResult<VtonResult>): VtonError =
        ((result as DataResult.Failure).error as VtonException).error

    @Test
    fun defaultCapabilities_areAsDeclared() {
        val engine = MockVtonEngine(StandardTestDispatcher(), concatCompositor)

        val expected = VtonCapabilities(
            supportedCategories = setOf(
                GarmentCategory.HEADWEAR,
                GarmentCategory.TOP,
                GarmentCategory.BOTTOM,
                GarmentCategory.FULL_BODY,
                GarmentCategory.FOOTWEAR,
            ),
            supportsTopBottomOutfit = true,
            maxParallelJobs = 1,
            requiredAspect = AspectRequirement.Unconstrained,
        )
        assertEquals(expected, engine.capabilities)
    }

    @Test
    fun defaultRenderDelay_is800ms() {
        assertEquals(800L, MockVtonEngine.DEFAULT_RENDER_DELAY.inWholeMilliseconds)
    }

    @Test
    fun render_onInjectedDispatcher_returnsDeterministicComposite() = runTest {
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), concatCompositor)
        val request = VtonRequest(
            baselineImage = baseline,
            garments = mapOf(GarmentCategory.BOTTOM to bottomBytes, GarmentCategory.TOP to topBytes),
        )

        val result = engine.render(request)

        assertArrayEquals(byteArrayOf(1, 2, 3, 10, 20), successImage(result))
    }

    @Test
    fun render_completesOnlyAfterInjectedDelayElapses() = runTest {
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), concatCompositor)
        val request = VtonRequest(baseline, mapOf(GarmentCategory.TOP to topBytes))

        val pending = async { engine.render(request) }
        advanceTimeBy(799)
        runCurrent()
        val completedEarly = pending.isCompleted
        advanceTimeBy(1)
        runCurrent()

        assertFalse(completedEarly)
        assertTrue(pending.isCompleted)
    }

    @Test
    fun render_isCancellableDuringDelay() = runTest {
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), concatCompositor)
        val request = VtonRequest(baseline, mapOf(GarmentCategory.TOP to topBytes))

        val pending = async { engine.render(request) }
        advanceTimeBy(400)
        pending.cancel()
        runCurrent()

        assertTrue(pending.isCancelled)
    }

    @Test
    fun render_withNoGarments_failsWithUnsupportedCombination() = runTest {
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), concatCompositor)

        val result = engine.render(VtonRequest(baseline, emptyMap()))

        assertEquals(VtonError.UnsupportedCombination, failureError(result))
    }

    @Test
    fun render_withCategoryOutsideCapabilities_failsWithUnsupportedCombination() = runTest {
        val capabilities = MockVtonEngine.DEFAULT_CAPABILITIES.copy(
            supportedCategories = setOf(GarmentCategory.TOP),
        )
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), concatCompositor, capabilities = capabilities)

        val result = engine.render(VtonRequest(baseline, mapOf(GarmentCategory.FOOTWEAR to topBytes)))

        assertEquals(VtonError.UnsupportedCombination, failureError(result))
    }

    @Test
    fun render_topAndBottomWithoutOutfitSupport_failsWithUnsupportedCombination() = runTest {
        val capabilities = MockVtonEngine.DEFAULT_CAPABILITIES.copy(supportsTopBottomOutfit = false)
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), concatCompositor, capabilities = capabilities)
        val request = VtonRequest(
            baselineImage = baseline,
            garments = mapOf(GarmentCategory.TOP to topBytes, GarmentCategory.BOTTOM to bottomBytes),
        )

        val result = engine.render(request)

        assertEquals(VtonError.UnsupportedCombination, failureError(result))
    }

    @Test
    fun render_whenCompositorCannotDecode_failsWithInvalidImage() = runTest {
        val engine = MockVtonEngine(StandardTestDispatcher(testScheduler), { _, _ -> null })

        val result = engine.render(VtonRequest(baseline, mapOf(GarmentCategory.TOP to topBytes)))

        assertEquals(VtonError.InvalidImage, failureError(result))
    }
}
