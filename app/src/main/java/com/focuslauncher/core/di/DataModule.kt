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
abstract class DataModule {
    @Binds
    abstract fun bindAppCatalogRepository(impl: AndroidAppCatalogRepository): AppCatalogRepository

    @Binds
    abstract fun bindAppIconLoader(impl: AndroidAppIconLoader): AppIconLoader
}
