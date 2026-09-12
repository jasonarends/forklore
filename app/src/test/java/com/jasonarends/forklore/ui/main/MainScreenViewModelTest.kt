package com.jasonarends.forklore.ui.main

import com.jasonarends.forklore.data.repository.DataRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScreenViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun uiState_isLoading_beforeRepositoryEmits() = runTest {
    val viewModel = MainScreenViewModel(FakeDataRepository())

    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_isSuccess_withRepositoryData() = runTest {
    val viewModel = MainScreenViewModel(FakeDataRepository())

    val state = viewModel.uiState.filterIsInstance<MainScreenUiState.Success>().first()

    assertEquals(listOf("Sample"), state.data)
  }
}

private class FakeDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}
