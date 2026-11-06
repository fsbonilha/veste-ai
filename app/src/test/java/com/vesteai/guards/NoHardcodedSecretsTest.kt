package com.vesteai.guards

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Detekt is not configured in this project, so this stands in for plan §6.4's "fail the build on a
 * hardcoded secret" rule. A key may only exist at runtime, entered by the user; nothing checked in
 * may contain one, in code, resources, the manifest, or Gradle wiring.
 *
 * Test sources are scanned too, because a fixture that looks like a live key is still a key an
 * `allowlist` would have to cover. Only obviously fake values pass: this file's own patterns are
 * assembled at runtime so the guard cannot flag itself.
 */
class NoHardcodedSecretsTest {

    private val repoRoot: File = findRepoRoot()

    @Test
    fun noLiveKeyIsCheckedIn() {
        val offenders = scan { line ->
            LIVE_KEY.containsMatchIn(line) && !THEMSELVES.containsMatchIn(line)
        }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun noKeyShapedLiteralIsAssignedInCheckedInConfig() {
        val offenders = scan(scope = setOf("gradle.properties", "local.properties", "AndroidManifest.xml")) { line ->
            KEY_LIKE_VALUE.containsMatchIn(line)
        }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun theGuardActuallySeesTheRepository() {
        val scanned = scanFiles().map { it.name }

        assertTrue("expected to scan Kotlin sources", scanned.any { it == "SettingsViewModel.kt" })
        assertTrue("expected to scan the manifest", scanned.any { it == "AndroidManifest.xml" })
    }

    /** Walks every tracked-looking file, skipping build output and the VCS directory. */
    private fun scanFiles(): List<File> = repoRoot.walkTopDown()
        .onEnter { it.name !in SKIPPED_DIRECTORIES }
        .filter { it.isFile && it.extension in SCANNED_EXTENSIONS }
        .toList()

    private fun scan(
        scope: Set<String>? = null,
        matches: (String) -> Boolean,
    ): List<String> = scanFiles()
        .filter { scope == null || it.name in scope }
        .flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> matches(line) }
                .map { (index, line) -> "${file.relativeTo(repoRoot)}:${index + 1}: ${line.trim()}" }
        }

    private fun findRepoRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (candidate.parentFile != null) {
            if (File(candidate, "settings.gradle.kts").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("Could not find the repository root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val SKIPPED_DIRECTORIES = setOf("build", ".git", ".gradle", ".idea", ".kotlin")

        val SCANNED_EXTENSIONS = setOf(
            "kt", "kts", "xml", "properties", "pro", "json", "toml", "txt", "yml", "yaml",
        )

        /** `pk_live_` plus at least a token's worth of key material. */
        val LIVE_KEY = Regex("pk_live_[A-Za-z0-9]{8,}")

        /** An obviously fake key, e.g. `pk_live_test` or `pk_live_abcdef123456` in a fixture. */
        val THEMSELVES = Regex("pk_live_(test|fake|dummy|placeholder|xxxx|sample|abc)")

        /** A secret-looking assignment rather than prose. */
        val KEY_LIKE_VALUE = Regex(
            "(?i)(api[_-]?key|secret|token)\\s*[=:]\\s*[\"']?[A-Za-z0-9_\\-]{16,}",
        )
    }
}