package com.vesteai.data.vton

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.tink.AeadSerializer
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.vesteai.core.credentials.API_KEY_PREFIX
import com.vesteai.core.credentials.CredentialState
import com.vesteai.core.credentials.SaveOutcome
import com.vesteai.core.credentials.maskApiKey
import com.vesteai.core.credentials.validateApiKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-disk shape of the BYOK entry. It is only ever handed to [AeadSerializer], which encrypts
 * it before a byte reaches the file, so nothing here is at rest in the clear.
 */
internal data class StoredCredentials(
    val apiKey: String = "",
    val baseUrl: String = "",
)

/** Explicit field encoding; `data` deliberately pulls in no serialization runtime for this. */
internal object StoredCredentialsSerializer : Serializer<StoredCredentials> {
    override val defaultValue: StoredCredentials = StoredCredentials()

    override suspend fun readFrom(input: InputStream): StoredCredentials {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        val buffer = ByteBuffer.wrap(bytes)
        return try {
            StoredCredentials(apiKey = buffer.readField(), baseUrl = buffer.readField())
        } catch (e: BufferUnderflowException) {
            throw androidx.datastore.core.CorruptionException("Truncated credential record", e)
        }
    }

    override suspend fun writeTo(t: StoredCredentials, output: OutputStream) {
        val buffer = ByteArrayOutputStream()
        buffer.writeField(t.apiKey)
        buffer.writeField(t.baseUrl)
        output.write(buffer.toByteArray())
    }

    private fun ByteBuffer.readField(): String {
        val field = ByteArray(getInt())
        get(field)
        return String(field, Charsets.UTF_8)
    }

    private fun ByteArrayOutputStream.writeField(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        write(bytes)
    }
}

/**
 * Where the credential file's [Aead] comes from. Null means "no usable cipher", which callers treat
 * as "no key readable", so a broken Keystore degrades to the Mock engine instead of crashing.
 */
fun interface CredentialCipherFactory {
    fun create(): Aead?

    /** Called after the cipher proved unusable so the next [create] starts from a fresh keyset. */
    fun wipe() = Unit
}

/**
 * Production cipher: a Tink keyset in the app's private prefs, wrapped by an AES key that never
 * leaves the Android Keystore. A corrupt or unreadable keyset surfaces as null from [create].
 */
class KeystoreCredentialCipherFactory(
    private val context: Context,
    private val prefsName: String = KEYSTORE_PREFS_NAME,
    private val keysetName: String = KEYSTORE_KEYSET_NAME,
    private val masterKeyUri: String = KEYSTORE_MASTER_KEY_URI,
) : CredentialCipherFactory {

    override fun create(): Aead? = try {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, keysetName, prefsName)
            .withMasterKeyUri(masterKeyUri)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IOException) {
        null
    }

    override fun wipe() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            clear()
            commit()
        }
        try {
            AndroidKeystoreKmsClient().deleteKey(masterKeyUri)
        } catch (e: GeneralSecurityException) {
            // Nothing to delete: the master key was never created, or it is already gone.
        }
    }

    companion object {
        const val KEYSTORE_PREFS_NAME = "vton_keyset"
        const val KEYSTORE_KEYSET_NAME = "vton_credential_keyset"
        const val KEYSTORE_MASTER_KEY_URI = "android-keystore://vton_credential_master_key"
    }
}

/**
 * Keystore-backed BYOK storage (plan §6.4). The entry lives in a `datastore-tink` file encrypted by
 * [AeadSerializer] over an `EncryptedFile`-equivalent keyset, so the key never touches Room,
 * plaintext preferences, logcat, or a crash report.
 *
 * Recovery from a corrupt install, in one rule: **a record is never left readable-looking and
 * undecryptable.** A keyset that will not open is wiped *and* the record it once protected is
 * deleted, so what is on disk stays decryptable by the keyset beside it, a failed read means "no
 * key", and a save made right after a wipe is still there after a restart.
 *
 * There is exactly one [DataStore] per process: DataStore refuses a second instance on the same file
 * and the scope here lives as long as the app. A record that will not decrypt under a keyset that
 * *does* open is therefore replaced in place by [ReplaceFileCorruptionHandler], which writes the new
 * record under the current keyset, rather than by tearing the store down.
 */
@Singleton
class EncryptedCredentialStore @Inject constructor(
    private val cipherFactory: CredentialCipherFactory,
    private val file: File,
    private val scope: CoroutineScope,
) : CredentialStore {

    private val mutex = Mutex()
    private val changes = MutableStateFlow(0)

    private var cached: DataStore<StoredCredentials>? = null

    /** A key was stored and can no longer be read; Settings must ask for it again. */
    private var lostKey = false

    override suspend fun read(): VtonCredentials? = mutex.withLock { stored()?.toCredentials() }

    override fun observe(): Flow<CredentialState> =
        changes.map { mutex.withLock { currentState() } }

    override suspend fun state(): CredentialState = mutex.withLock { currentState() }

    override suspend fun save(apiKey: String, baseUrl: String): SaveOutcome {
        validateApiKey(apiKey, API_KEY_PREFIX)?.let { return SaveOutcome.InvalidFormat(it) }
        return mutex.withLock {
            val store = dataStore() ?: return SaveOutcome.StorageUnavailable
            try {
                store.updateData { StoredCredentials(apiKey.trim(), baseUrl.trim()) }
                lostKey = false
                changes.value++
                SaveOutcome.Saved
            } catch (e: IOException) {
                SaveOutcome.Failed
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            val store = cached
            if (store != null) {
                try {
                    store.updateData { StoredCredentials() }
                } catch (e: IOException) {
                    // Zeroizing failed, so remove the record instead. The live store recreates an
                    // empty one under the same keyset on its next write.
                    dropRecord()
                }
            } else {
                wipeAll()
            }
            lostKey = false
        }
        changes.value++
    }

    private suspend fun currentState(): CredentialState =
        if (lostKey) CredentialState.Unreadable else stored()?.toState() ?: CredentialState.Absent

    /**
     * Reads the entry, or null when there is nothing readable. A failed read means the key is gone,
     * so whatever produced it is dropped too, leaving a clean slate for the next save.
     */
    private suspend fun stored(): StoredCredentials? {
        val store = dataStore() ?: return null
        return try {
            store.data.first()
        } catch (e: IOException) {
            lostKey = true
            dropRecord()
            null
        }
    }

    private fun dataStore(): DataStore<StoredCredentials>? {
        cached?.let { return it }
        val hadRecord = file.exists()
        val cipher = createCipher(hadRecord) ?: return null
        val store = androidx.datastore.core.DataStoreFactory.create(
            serializer = AeadSerializer(cipher, StoredCredentialsSerializer, ASSOCIATED_DATA),
            corruptionHandler = ReplaceFileCorruptionHandler {
                // The keyset opened but the record did not. Nothing in it is recoverable; the
                // replacement record is written under the current keyset, so saves work from here.
                if (hadRecord) lostKey = true
                StoredCredentials()
            },
            scope = scope,
            produceFile = { file },
        )
        cached = store
        return store
    }

    /**
     * Opens the [Aead] for the record. A keyset that will not open is unrecoverable, so it is wiped
     * along with the record it protected — no live store is holding the file at this point — and the
     * next save starts from a fresh keyset paired with a fresh record.
     */
    private fun createCipher(hadRecord: Boolean): Aead? {
        cipherFactory.create()?.let { return it }
        if (hadRecord) lostKey = true
        wipeAll()
        return null
    }

    private fun wipeAll() {
        cached = null
        dropRecord()
        cipherFactory.wipe()
    }

    /**
     * Removes the encrypted record but keeps the live store and its keyset. The store recreates the
     * file on its next write, so this never leaves the process with two DataStores on one path.
     */
    private fun dropRecord() {
        file.delete()
    }

    private fun StoredCredentials.toCredentials(): VtonCredentials? =
        if (apiKey.isBlank()) null else VtonCredentials(apiKey = apiKey, baseUrl = baseUrl)

    private fun StoredCredentials.toState(): CredentialState =
        if (apiKey.isBlank()) {
            CredentialState.Absent
        } else {
            CredentialState.Present(maskedKey = maskApiKey(apiKey), baseUrl = baseUrl)
        }

    companion object {
        const val FILE_NAME = "vton_credentials.preferences_pb"

        /** A unique associated datum per file, so one keyset cannot decrypt another file's bytes. */
        private val ASSOCIATED_DATA = FILE_NAME.toByteArray()

        fun applicationScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}