package com.vesteai.core.di

import android.content.Context
import com.vesteai.core.credentials.CredentialSettings
import com.vesteai.data.vton.CredentialCipherFactory
import com.vesteai.data.vton.CredentialStore
import com.vesteai.data.vton.EncryptedCredentialStore
import com.vesteai.data.vton.EncryptedVtonCredentialProvider
import com.vesteai.data.vton.KeystoreCredentialCipherFactory
import com.vesteai.data.vton.SettingsCredentialSettings
import com.vesteai.data.vton.VtonCredentialProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CredentialsModule {
    @Provides
    @Singleton
    fun provideCredentialCipherFactory(@ApplicationContext context: Context): CredentialCipherFactory =
        KeystoreCredentialCipherFactory(context)

    @Provides
    @Singleton
    fun provideCredentialFile(@ApplicationContext context: Context): File =
        File(context.filesDir, EncryptedCredentialStore.FILE_NAME)

    @Provides
    @Singleton
    fun provideCredentialScope(): CoroutineScope = EncryptedCredentialStore.applicationScope()

    @Provides
    @Singleton
    fun provideEncryptedCredentialStore(
        cipherFactory: CredentialCipherFactory,
        file: File,
        scope: CoroutineScope,
    ): EncryptedCredentialStore = EncryptedCredentialStore(cipherFactory, file, scope)

    @Provides
    @Singleton
    fun provideCredentialSettings(store: EncryptedCredentialStore): CredentialSettings =
        SettingsCredentialSettings(store)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CredentialBindingsModule {
    @Binds
    abstract fun bindVtonCredentialProvider(
        impl: EncryptedVtonCredentialProvider,
    ): VtonCredentialProvider

    @Binds
    abstract fun bindCredentialStore(impl: EncryptedCredentialStore): CredentialStore
}