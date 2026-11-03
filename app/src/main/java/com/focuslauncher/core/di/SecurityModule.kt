package com.focuslauncher.core.di

import android.content.Context
import com.focuslauncher.core.security.AdminEnvironment
import com.focuslauncher.core.security.AdminSessionManager
import com.focuslauncher.core.security.AndroidAdminEnvironment
import com.focuslauncher.core.security.PinHasher
import com.focuslauncher.core.security.Pbkdf2PinHasher
import com.focuslauncher.data.repository.AdminAccessCoordinator
import com.focuslauncher.domain.repository.AdminRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminBindingsModule {
    @Binds
    abstract fun bindAdminRepository(impl: AdminAccessCoordinator): AdminRepository

    @Binds
    abstract fun bindOnboardingRepository(impl: AdminAccessCoordinator): com.focuslauncher.domain.repository.OnboardingRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideAdminEnvironment(@ApplicationContext context: Context): AdminEnvironment = AndroidAdminEnvironment(context)

    @Provides
    @Singleton
    fun provideAdminSessionManager(
        clock: MonotonicClock,
        environment: AdminEnvironment,
        dispatchers: DispatcherProvider,
    ): AdminSessionManager =
        AdminSessionManager(clock, environment, CoroutineScope(SupervisorJob() + dispatchers.default))

    /** La KDF corre en Default con concurrencia 1: nunca dos derivaciones en paralelo. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun providePinHasher(dispatchers: DispatcherProvider): PinHasher =
        Pbkdf2PinHasher(dispatchers.default.limitedParallelism(1))
}
