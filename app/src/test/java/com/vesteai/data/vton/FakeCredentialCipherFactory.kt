package com.vesteai.data.vton

import com.google.crypto.tink.Aead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stand-in for [KeystoreCredentialCipherFactory] on the JVM. `AndroidKeyStore` does not exist there,
 * so Tink cannot wrap a keyset with it at all; this returns an equivalent AES-GCM [Aead] backed by
 * `javax.crypto` and a fixed in-test key instead.
 *
 * Everything else under test is the production path — [com.google.crypto.tink.streamingaead
 * .StreamingAeadStyle] encryption through `AeadSerializer`, the DataStore file, the record format,
 * and the wipe-and-reenter recovery — so these tests cover the store, not a reimplementation of it.
 * What they cannot cover is the Keystore itself; see the milestone report.
 *
 * - [unavailable] models a Keystore that cannot be opened at all: every [create] returns null.
 * - [rotateKeyset] models a Keystore entry that is gone: the same store now yields a different key,
 *   so an existing record becomes undecryptable, which `AeadSerializer` reports as a
 *   `CorruptionException`. [corrupt] models a store that cannot open a keyset it did have.
 */
internal class FakeCredentialCipherFactory : CredentialCipherFactory {

    var unavailable: Boolean = false
    /** The keyset is present but will not open; the next [create] fails like a corrupt install. */
    var corrupt: Boolean = false
    var createCount: Int = 0
        private set
    var wipeCount: Int = 0
        private set

    override fun create(): Aead? {
        createCount++
        if (unavailable) return null
        if (corrupt) return null
        return AesGcmAead(key)
    }

    override fun wipe() {
        wipeCount++
        // A real wipe discards the Keystore entry, so the next attempt uses a brand-new key. Any
        // record encrypted under the old one is then undecryptable, which is what the caller wants.
        corrupt = false
        rotateKeyset()
    }

    /** Models a Keystore entry that is gone: the same store now yields a different key. */
    fun rotateKeyset() {
        key = SecretKeySpec(SecureRandom().generateSeed(KEY_BYTES), "AES").encoded
    }

    private var key: ByteArray = SecureRandom().generateSeed(KEY_BYTES)

    private companion object {
        const val KEY_BYTES = 16
    }
}

/** Minimal AES-GCM [Aead]: nonce prefix + ciphertext, associated data authenticated. */
internal class AesGcmAead(private val keyBytes: ByteArray) : Aead {
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(associatedData ?: ByteArray(0))
        return nonce + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
        if (ciphertext.size <= NONCE_BYTES) throw GeneralSecurityException("ciphertext too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_BITS, ciphertext, 0, NONCE_BYTES),
        )
        cipher.updateAAD(associatedData ?: ByteArray(0))
        return cipher.doFinal(ciphertext, NONCE_BYTES, ciphertext.size - NONCE_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Builds stores over one credential file and swaps in a fresh one to model a process restart. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CredentialStoreHarness(root: File) {
    val credentialFile = File(root, EncryptedCredentialStore.FILE_NAME)
    val cipherFactory = FakeCredentialCipherFactory()

    private var scope = newScope()

    var store: EncryptedCredentialStore = newStore()
        private set

    /** Raw file bytes, for asserting nothing readable landed on disk. */
    val storedBytes: ByteArray get() = credentialFile.readBytes()

    fun restart(): EncryptedCredentialStore {
        scope.cancel()
        scope = newScope()
        return newStore().also { store = it }
    }

    fun close() {
        scope.cancel()
    }

    private fun newStore() = EncryptedCredentialStore(cipherFactory, credentialFile, scope)

    private fun newScope() = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
}