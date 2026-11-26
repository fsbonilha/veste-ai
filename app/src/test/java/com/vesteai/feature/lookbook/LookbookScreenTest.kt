package com.vesteai.feature.lookbook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vesteai.R
import com.vesteai.domain.model.Garment
import com.vesteai.domain.model.GarmentCategory
import com.vesteai.domain.model.Outfit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LookbookScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private val top = Garment(
        id = "top",
        imagePath = "wardrobe/top.jpg",
        category = GarmentCategory.TOP,
        createdAt = 1L,
        isFavorite = false,
    )
    private val jeans = Garment(
        id = "jeans",
        imagePath = "wardrobe/jeans.jpg",
        category = GarmentCategory.BOTTOM,
        createdAt = 2L,
        isFavorite = false,
    )
    private val cards = listOf(
        LookCard(
            Outfit(
                id = "look-1",
                resultImagePath = "looks/look-1.jpg",
                createdAt = 1_710_000_367_000L,
                isFavorite = false,
                garments = listOf(top, jeans),
            ),
            createdLabel = "Mar 9, 2024",
        ),
    )

    private fun showScreen(
        state: LookbookUiState,
        onToggleFavorite: (LookCard) -> Unit = {},
        onReload: (LookCard) -> Unit = {},
        onRequestDelete: (LookCard) -> Unit = {},
        onConfirmDelete: () -> Unit = {},
    ) {
        compose.setContent {
            LookbookScreen(
                state = state,
                imageFile = { File(it) },
                onRetry = {},
                onToggleFavorite = onToggleFavorite,
                onReload = onReload,
                onRequestDelete = onRequestDelete,
                onConfirmDelete = onConfirmDelete,
                onCancelDelete = {},
                onDismissDeleteError = {},
                onCancelJob = {},
                onRetryJob = {},
                onSaveJobToLookbook = {},
                onDiscardJob = {},
                onReloadJob = {},
            )
        }
    }

    private fun content(vararg cards: LookCard) = LookbookUiState.Content(looks = cards.toList())

    @Test
    fun cardShowsTheCreationDate() {
        showScreen(content(*cards.toTypedArray()))

        compose.onNodeWithText(resources.getString(R.string.lookbook_saved_on, "Mar 9, 2024"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun cardActionsAreDisplayed() {
        showScreen(content(*cards.toTypedArray()))

        listOf(
            R.string.action_favorite,
            R.string.action_delete,
            R.string.action_reload_in_fitting_room,
        ).forEach { res ->
            compose.onNodeWithText(resources.getString(res))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun favoriteActionReportsTheCard() {
        var toggled: LookCard? = null
        showScreen(content(*cards.toTypedArray()), onToggleFavorite = { toggled = it })

        compose.onNodeWithText(resources.getString(R.string.action_favorite))
            .performScrollTo()
            .performClick()

        assertEquals("look-1", toggled?.outfit?.id)
    }

    @Test
    fun reloadActionReportsTheCard() {
        var reloaded: LookCard? = null
        showScreen(content(*cards.toTypedArray()), onReload = { reloaded = it })

        compose.onNodeWithText(resources.getString(R.string.action_reload_in_fitting_room))
            .performScrollTo()
            .performClick()

        assertEquals(listOf("top", "jeans"), reloaded?.outfit?.garments?.map { it.id })
    }

    @Test
    fun deleteActionRequestsConfirmationWithoutDeleting() {
        var requested: LookCard? = null
        var confirmed = 0
        showScreen(
            content(*cards.toTypedArray()),
            onRequestDelete = { requested = it },
            onConfirmDelete = { confirmed++ },
        )

        compose.onNodeWithText(resources.getString(R.string.action_delete))
            .performScrollTo()
            .performClick()

        assertEquals("look-1", requested?.outfit?.id)
        assertEquals(0, confirmed)
    }

    @Test
    fun pendingDeletionShowsTheDestructiveDialog() {
        showScreen(
            LookbookUiState.Content(looks = cards, pendingDeletion = cards.single()),
            onConfirmDelete = {},
        )

        compose.onNodeWithText(resources.getString(R.string.delete_lookbook_title))
            .assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.delete_lookbook_body))
            .assertIsDisplayed()
    }

    @Test
    fun emptyLookbookExplainsHowToFillIt() {
        showScreen(content())

        compose.onNodeWithText(resources.getString(R.string.lookbook_empty)).assertIsDisplayed()
    }

    @Test
    fun loadFailureShowsRetryInsteadOfContent() {
        showScreen(LookbookUiState.Error)

        compose.onNodeWithText(resources.getString(R.string.lookbook_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun loadingStateShowsNoCardsAndNoEmptyMessage() {
        showScreen(LookbookUiState.Loading)

        compose.onNodeWithText(resources.getString(R.string.lookbook_empty)).assertDoesNotExist()
    }

    @Test
    fun deleteFailureShowsAMessage() {
        showScreen(LookbookUiState.Content(looks = cards, deleteFailed = true))

        compose.onNodeWithText(resources.getString(R.string.lookbook_delete_failed))
            .assertIsDisplayed()
    }
}
