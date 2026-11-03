package com.focuslauncher

import com.focuslauncher.core.di.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/** Todos los dispatchers apuntan al mismo dispatcher de prueba. */
class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main = dispatcher
    override val io = dispatcher
    override val default = dispatcher
}
