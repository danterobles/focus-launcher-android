package com.focuslauncher.core.di

import com.focuslauncher.core.home.HomeRoleController
import com.focuslauncher.core.home.RoleManagerHomeRoleController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    abstract fun bindHomeRoleController(impl: RoleManagerHomeRoleController): HomeRoleController
}
