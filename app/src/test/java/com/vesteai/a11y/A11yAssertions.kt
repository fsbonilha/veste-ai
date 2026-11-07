package com.vesteai.a11y

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import java.io.File

/** Material's minimum interactive size. Restated here because a test cannot see app internals. */
internal val MinimumTouchTarget = 48.dp

/**
 * Shared audit helpers for the M8 accessibility pass. They work off the public semantics of a
 * screen only: what a screen reader sees is exactly this, so nothing here needs a Material
 * internal, and a control added later without a label fails the same assertion.
 */
internal object A11y {

    /**
     * True when a node with a click action has nothing to announce. Visible text (a button label),
     * a content description (an icon-only control) and editable text (a field whose label sits
     * above it) all count as a name.
     */
    fun isUnlabelled(node: SemanticsNode): Boolean {
        val hasText = node.config.getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .any { it.text.isNotBlank() }
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)
        val hasDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()
            .any { it.isNotBlank() }
        return !(hasText || hasDescription || (editable != null && editable.text.isNotBlank()))
    }

    /**
     * Touch bounds, not layout bounds: a small painted control (a chip's label area) can still
     * expose a full-size target, and that is the size a thumb actually gets.
     */
    fun isTouchSized(node: SemanticsNode): Boolean =
        node.touchBoundsInRoot.width >= 48f && node.touchBoundsInRoot.height >= 48f

    /**
     * A control that is scrolled entirely out of its viewport has no touch area left to measure,
     * and one that was never composed has none at all. Both are skipped rather than failed: the
     * audit scrolls everything into view first, and what is left here was not measurable.
     */
    fun isMeasurable(node: SemanticsNode): Boolean =
        node.touchBoundsInRoot.width > 0f && node.touchBoundsInRoot.height > 0f

    /**
     * Gesture affordances Material adds to its own modal sheet chrome (the drag handle). They are
     * not app controls, they are not in the app's layout, and the app cannot resize them.
     */
    fun isPlatformChrome(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()
            .any { it == PlatformSheetDragHandle }

    fun describe(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .joinToString(" | ") { it.text }
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            .joinToString(" | ")
        return "text=[$text] contentDescription=[$description] bounds=${node.boundsInRoot}"
    }
}

/** Material's own label for the modal sheet drag handle. */
private const val PlatformSheetDragHandle = "Drag handle"

/**
 * Every control the app itself exposes. Material's sheet chrome is excluded from the touch-target
 * audit because the app does not lay it out; everything else is included, named or not.
 */
internal fun ComposeContentTestRule.appControls(): List<SemanticsNode> =
    interactiveNodes().filterNot { A11y.isPlatformChrome(it) }

internal fun ComposeContentTestRule.interactiveNodes(): List<SemanticsNode> =
    onAllNodes(hasClickAction()).fetchSemanticsNodes()

/**
 * Every control that is on screen must be nameable by a screen reader and at least 48dp tall and
 * wide. Controls are scrolled into view first: a node below the fold is not laid out, and its
 * bounds would read as zero.
 */
internal fun ComposeContentTestRule.assertControlsAreAccessible() {
    // A control at the top of a long list can only be partly scrolled into view, so one pass is
    // not enough to make every control measurable: repeat from each end until the count settles.
    repeat(3) {
        val interactions = onAllNodes(hasClickAction())
        val order = if (it % 2 == 0) interactions.fetchSemanticsNodes().indices.reversed()
        else interactions.fetchSemanticsNodes().indices
        order.forEach { index ->
            runCatching { interactions[index].performScrollTo() }
        }
    }

    val nodes = appControls()
    val unnamed = nodes.filter { A11y.isUnlabelled(it) }
    check(unnamed.isEmpty()) {
        "Controls with no accessible name: " + unnamed.joinToString { A11y.describe(it) }
    }

    val undersized = nodes.filter(A11y::isMeasurable).filterNot(A11y::isTouchSized)
    check(undersized.isEmpty()) {
        "Touch targets smaller than 48dp: " +
            undersized.joinToString { "${it.touchBoundsInRoot} ${A11y.describe(it)}" }
    }
}

/** The primary action has to stay reachable and legible, whatever the font scale. */
internal fun ComposeContentTestRule.assertPrimaryActionReachable(text: String) {
    val node = onNodeWithText(text)
    // Scrolling only applies where a screen actually scrolls; a fixed action bar has nothing to
    // scroll and would otherwise fail the assertion for the wrong reason.
    runCatching { node.performScrollTo() }
    node.assertIsDisplayed().assertHeightIsAtLeast(MinimumTouchTarget)
}

internal fun ComposeContentTestRule.assertTextDisplayed(text: String) {
    onNodeWithText(text).assertIsDisplayed()
}

/** For copy that sits below the fold on a scrolling screen: scrolled to, then displayed. */
internal fun ComposeContentTestRule.assertTextReachable(text: String) {
    onNodeWithText(text).performScrollTo().assertIsDisplayed()
}

/** A produced image must carry the description the screen declares for it, not be decorative. */
internal fun ComposeContentTestRule.assertImageHasDescription(description: String) {
    val matches = onAllNodesWithContentDescription(description).fetchSemanticsNodes()
    check(matches.isNotEmpty()) { "No image described as '$description' is on screen" }
}

/** A temp image with real bytes, so an image loader has something to accept. */
internal fun tempImage(name: String): File = File.createTempFile(name, ".jpg").apply {
    writeBytes(byteArrayOf(0, 1, 2, 3))
    deleteOnExit()
}

/**
 * Every string a screen currently puts in front of the user: visible text and content
 * descriptions. Used to prove no raw throwable text ever reaches either.
 */
internal fun ComposeContentTestRule.wordings(): List<String> =
    interactiveNodesAndText().flatMap { node ->
        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        texts + descriptions + listOfNotNull(editable)
    }.filter { it.isNotBlank() }

private fun ComposeContentTestRule.interactiveNodesAndText(): List<SemanticsNode> =
    onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes()
