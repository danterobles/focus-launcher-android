package com.focuslauncher

import com.focuslauncher.presentation.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleUnitTest {
    @Test
    fun homeUiState_initialMarkerIsLoading() {
        assertEquals("Loading", HomeUiState.Loading.toString())
    }
}
