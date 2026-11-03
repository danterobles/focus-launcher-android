package com.focuslauncher.core.di

import android.content.Context
import com.focuslauncher.core.security.KeystoreStateCipher
import com.focuslauncher.core.security.StateCipher
import com.focuslauncher.data.local.FocusStateStore
import com.focuslauncher.data.local.StateLocation
import com.focuslauncher.data.repository.FocusStateRepositoryImpl
import com.focuslauncher.domain.repository.FocusStateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindingsModule {
    @Binds
    abstract fun bindFocusStateRepository(impl: FocusStateRepositoryImpl): FocusStateRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideStateCipher(): StateCipher = KeystoreStateCipher(KeystoreStateCipher.DEFAULT_ALIAS)

    /** Un único archivo, excluido de copias: `noBackupFilesDir/focus/focus_state.pb.enc`. */
    @Provides
    @Singleton
    fun provideStateLocation(@ApplicationContext context: Context): StateLocation =
        StateLocation(File(File(context.noBackupFilesDir, "focus"), "focus_state.pb.enc"))

    @Provides
    @Singleton
    fun provideFocusStateStore(
        @ApplicationContext context: Context,
        location: StateLocation,
        cipher: StateCipher,
        dispatchers: DispatcherProvider,
    ): FocusStateStore = FocusStateStore(
        location = location,
        cipher = cipher,
        scope = CoroutineScope(SupervisorJob() + dispatchers.io),
        ownPackage = context.packageName,
    )
}
