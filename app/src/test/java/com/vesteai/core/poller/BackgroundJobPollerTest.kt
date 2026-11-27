package com.vesteai.core.poller

import com.vesteai.core.media.ImageStore
import com.vesteai.core.result.DataResult
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.repository.JobRepository
import com.vesteai.domain.repository.LookbookRepository
import com.vesteai.domain.repository.WardrobeRepository
import com.vesteai.domain.model.Outfit
import com.vesteai.domain.vton.AspectRequirement
import com.vesteai.domain.vton.JobStatus
import com.vesteai.domain.vton.PendingJob
import com.vesteai.domain.vton.VtonCapabilities
import com.vesteai.domain.vton.VtonEngine
import com.vesteai.domain.vton.VtonError
import com.vesteai.domain.vton.VtonPollResult
import com.vesteai.domain.vton.VtonRequest
import com.vesteai.domain.vton.VtonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.milliseconds

/**
 * The poller must never leave a job row non-terminal by accident: `observeActive()` only re-emits
 * when the table changes, and a job id already cleared from its bookkeeping is never retried, so a
 * half-finished job would sit in the Ongoing list forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundJobPollerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeJobRepository(initial: List<PendingJob>) : JobRepository {
        val jobs = MutableStateFlow(initial)

        override fun observeActive(): Flow<List<PendingJob>> =
            jobs.map { list -> list.filter { it.isActive } }

        override fun observeAll(): Flow<List<PendingJob>> = jobs

        override suspend fun save(job: PendingJob): DataResult<PendingJob> {
            jobs.value = jobs.value.filterNot { it.id == job.id } + job
            return DataResult.Success(job)
        }

        override suspend fun getById(id: String): PendingJob? = jobs.value.firstOrNull { it.id == id }

        override suspend fun updateProviderJobId(id: String, providerJobId: String) {
            update(id) { it.copy(providerJobId = providerJobId) }
        }

        override suspend fun transitionToProcessing(id: String) {
            update(id) { it.copy(status = JobStatus.Processing) }
        }

        override suspend fun complete(id: String, resultImagePath: String) {
            update(id) { it.copy(status = JobStatus.Completed(resultImagePath)) }
        }

        override suspend fun fail(id: String, error: VtonError, guidance: String?) {
            update(id) { it.copy(status = JobStatus.Failed(error, guidance)) }
        }

        override suspend fun delete(id: String) {
            jobs.value = jobs.value.filterNot { it.id == id }
        }

        private fun update(id: String, transform: (PendingJob) -> PendingJob) {
            jobs.value = jobs.value.map { if (it.id == id) transform(it) else it }
        }
    }

    /** Polls blow up with a non-mapped throwable, as an unexpected bug in the engine would. */
    private class ExplodingEngine : VtonEngine {
        override val displayName: String = "exploding"
        override val capabilities: VtonCapabilities = VtonCapabilities(
            supportedCategories = GarmentCategory.entries.toSet(),
            supportsTopBottomOutfit = true,
            maxParallelJobs = 1,
            requiredAspect = AspectRequirement.Unconstrained,
            isAsync = true,
        )

        override suspend fun submit(request: VtonRequest): DataResult<String> =
            DataResult.Success("provider-job")

        override suspend fun poll(jobId: String): DataResult<VtonPollResult?> =
            throw IllegalStateException("engine blew up")
    }

    private class NoopLookbook : LookbookRepository {
        override fun observeAll(): Flow<List<Outfit>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Outfit? = null
        override suspend fun save(resultImagePath: String, garmentIds: Collection<String>) =
            DataResult.Success(
                Outfit("o", resultImagePath, 0L, false, emptyList()),
            )
        override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class NoopWardrobe : WardrobeRepository {
        override fun observeAll(): Flow<List<Garment>> = MutableStateFlow(emptyList())
        override fun observeByCategory(category: GarmentCategory): Flow<List<Garment>> =
            MutableStateFlow(emptyList())
        override suspend fun getByIds(ids: Collection<String>): List<Garment> = emptyList()
        override suspend fun add(imagePath: String, category: GarmentCategory, displayName: String?) =
            error("not used")
        override suspend fun setFavorite(id: String, isFavorite: Boolean) = Unit
        override suspend fun countLookbooksContaining(garmentId: String): Int = 0
        override suspend fun deleteGarment(garmentId: String): DataResult<Unit> =
            DataResult.Success(Unit)
    }

    @Test
    fun anUnexpectedPollFailureLeavesNoNonTerminalJob() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val job = PendingJob(
            id = "j1",
            providerName = "exploding",
            providerJobId = "provider-job",
            garmentIds = listOf("g1"),
            baselineImagePath = ImageStore.BASELINE_PATH,
            submittedAt = 0L,
            status = JobStatus.Processing,
        )
        val jobRepo = FakeJobRepository(listOf(job))
        val poller = BackgroundJobPoller(
            engine = ExplodingEngine(),
            jobRepo = jobRepo,
            lookbook = NoopLookbook(),
            wardrobe = NoopWardrobe(),
            imageStore = ImageStore(tmp.root),
            ioDispatcher = dispatcher,
        ).apply { pollInterval = 1.milliseconds }

        // The poller's watcher is a long-lived collector, so it belongs in backgroundScope; the
        // tiny poll interval makes the submit/poll loop run to its terminal state deterministically.
        // An explicit scope, cancelled at the end: the poller's watcher is a long-lived collector,
        // so it cannot live on the test's own scope or runTest would wait for it forever.
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        poller.start(scope)
        testScheduler.advanceUntilIdle()

        // The explosion was caught and recorded as a failure, so the row is terminal.
        val remaining = jobRepo.jobs.value.single()
        assertTrue(
            "expected a terminal status, was ${remaining.status}",
            remaining.status.isTerminal,
        )
        assertEquals(JobStatus.Failed(VtonError.JobFailed("engine blew up"), null), remaining.status)
        scope.cancel()
    }
}
