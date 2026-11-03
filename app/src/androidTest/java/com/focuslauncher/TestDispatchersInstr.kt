package com.focuslauncher

import com.focuslauncher.core.di.DispatcherProvider
import kotlinx.coroutines.Dispatchers

class TestDispatchersInstr : DispatcherProvider {
    override val main = Dispatchers.Main
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
}
