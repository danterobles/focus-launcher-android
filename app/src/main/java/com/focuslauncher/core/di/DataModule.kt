package com.focuslauncher.core.di

import com.focuslauncher.core.ui.AppIconLoader
import com.focuslauncher.data.android.AndroidAppCatalogRepository
import com.focuslauncher.data.android.AndroidAppIconLoader
import com.focuslauncher.domain.repository.AppCatalogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object OwnPackageModule {
    @dagger.Provides
    fun provideOwnPackage(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): com.focuslauncher.domain.usecase.OwnPackage =
        com.focuslauncher.domain.usecase.OwnPackage(context.packageName)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAppCatalogRepository(impl: AndroidAppCatalogRepository): AppCatalogRepository

    @Binds
    abstract fun bindAppIconLoader(impl: AndroidAppIconLoader): AppIconLoader
}
