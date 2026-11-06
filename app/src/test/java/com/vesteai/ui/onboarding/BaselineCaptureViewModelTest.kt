package com.vesteai.ui.onboarding

import com.vesteai.core.media.ImageImporter
import com.vesteai.core.media.ImageNormalizer
import com.vesteai.core.media.ImageStore
import com.vesteai.core.media.writeTestJpeg
import com.vesteai.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BaselineCaptureViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val profile = FakeProfileRepository()
    private val store by lazy { ImageStore(File(tmp.root, "files")) }
    private val viewModel by lazy {
        BaselineCaptureViewModel(
            imageImporter = ImageImporter(context, store, ImageNormalizer(), dispatcher),
            profileRepository = profile,
            clock = clock,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun capturedPortraitPhotoIsSavedAsBaseline() {
        val capture = File(tmp.root, "capture.jpg").also { writeTestJpeg(it, 600, 800) }

        viewModel.onCaptured(capture)

        assertEquals(BaselineCaptureState.Saved, viewModel.state.value)
    }

    @Test
    fun successfulSaveRecordsBaselineTimestampFromClock() {
        val capture = File(tmp.root, "capture.jpg").also { writeTestJpeg(it, 600, 800) }

        viewModel.onCaptured(capture)

        assertEquals(clock.millis(), profile.baselineSavedAtMillis.value)
    }

    @Test
    fun successfulSaveCompletesOnboarding() {
        val capture = File(tmp.root, "capture.jpg").also { writeTestJpeg(it, 600, 800) }

        viewModel.onCaptured(capture)

        assertTrue(profile.onboardingComplete)
    }

    @Test
    fun successfulSaveWritesBaselineFileAtFixedPath() {
        val capture = File(tmp.root, "capture.jpg").also { writeTestJpeg(it, 600, 800) }

        viewModel.onCaptured(capture)

        assertTrue(store.resolve("baseline/baseline.jpg").isFile)
    }

    @Test
    fun tooSmallPhotoFailsWithTooSmallAndDoesNotCompleteOnboarding() {
        val capture = File(tmp.root, "tiny.jpg").also { writeTestJpeg(it, 200, 200) }

        viewModel.onCaptured(capture)

        assertEquals(
            BaselineCaptureState.Failed(BaselineSaveError.TOO_SMALL),
            viewModel.state.value,
        )
        assertFalse(profile.onboardingComplete)
    }

    @Test
    fun storageFailureAfterWriteFailsWithStorageError() {
        profile.failOnMarkSaved = true
        val capture = File(tmp.root, "capture.jpg").also { writeTestJpeg(it, 600, 800) }

        viewModel.onCaptured(capture)

        assertEquals(
            BaselineCaptureState.Failed(BaselineSaveError.STORAGE_FAILED),
            viewModel.state.value,
        )
    }

    @Test
    fun retryAfterFailureReturnsToReady() {
        viewModel.onCaptureFailed()

        viewModel.onRetry()

        assertEquals(BaselineCaptureState.Ready, viewModel.state.value)
    }

    @Test
    fun captureFailureSurfacesCaptureError() {
        viewModel.onCaptureFailed()

        assertEquals(
            BaselineCaptureState.Failed(BaselineSaveError.CAPTURE_FAILED),
            viewModel.state.value,
        )
    }
}

private class FakeProfileRepository : ProfileRepository {
    private val onboarding = MutableStateFlow(false)
    private val baselinePresent = MutableStateFlow(false)
    val baselineSavedAtMillis = MutableStateFlow<Long?>(null)
    var failOnMarkSaved = false

    val onboardingComplete: Boolean get() = onboarding.value

    override fun observeOnboardingComplete(): StateFlow<Boolean> = onboarding.asStateFlow()

    override suspend fun completeOnboarding() {
        onboarding.value = true
    }

    override fun observeBaselinePresent(): StateFlow<Boolean> = baselinePresent.asStateFlow()

    override fun observeBaselineSavedAtMillis(): StateFlow<Long?> = baselineSavedAtMillis.asStateFlow()

    override suspend fun markBaselineSaved(savedAtMillis: Long) {
        if (failOnMarkSaved) throw IOException("disk full")
        baselinePresent.value = true
        baselineSavedAtMillis.value = savedAtMillis
    }
}
