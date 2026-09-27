package com.fardeenkhan.moodtune.domain.model

import app.cash.turbine.test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD coverage for [asLoadState] - the shared Loading/Loaded/Error reduction every ViewModel's
 * list-loading state field is built from. Written before [asLoadState] exists, per the
 * android-testing-approach skill's strict TDD rule for domain-layer logic.
 */
class LoadStateFlowTest {

    @Test
    fun `emits Loading as the first value, before the source emits anything`() = runTest {
        flowOf(listOf("a")).asLoadState().test {
            assertEquals(LoadState.Loading, awaitItem())
            awaitItem() // the Loaded value, asserted in the next test
            awaitComplete()
        }
    }

    @Test
    fun `emits Loaded with the source's value after Loading`() = runTest {
        flowOf(listOf("a", "b")).asLoadState().test {
            awaitItem() // Loading
            assertEquals(LoadState.Loaded(listOf("a", "b")), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `emits Loaded again for each subsequent value the source emits, without re-emitting Loading`() = runTest {
        flow {
            emit(listOf("a"))
            emit(listOf("a", "b"))
        }.asLoadState().test {
            assertEquals(LoadState.Loading, awaitItem())
            assertEquals(LoadState.Loaded(listOf("a")), awaitItem())
            assertEquals(LoadState.Loaded(listOf("a", "b")), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `emits Error with the thrown exception's message when the source throws`() = runTest {
        flow<List<String>> { throw IllegalStateException("permission denied") }
            .asLoadState()
            .test {
                awaitItem() // Loading
                assertEquals(LoadState.Error("permission denied"), awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `falls back to a generic message when the thrown exception has none`() = runTest {
        flow<List<String>> { throw RuntimeException() }
            .asLoadState()
            .test {
                awaitItem() // Loading
                val result = awaitItem()
                check(result is LoadState.Error) { "expected Error, got $result" }
                assert(result.message.isNotBlank())
                awaitComplete()
            }
    }
}
