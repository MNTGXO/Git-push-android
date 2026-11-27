package com.mntx.gitpulse.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal async value container — enough for this app, zero ceremony. */
sealed interface Async<out T> {
    data object Idle : Async<Nothing>
    class Loading internal constructor() : Async<Nothing> {
        override fun equals(other: Any?) = other is Loading
        override fun hashCode() = 0x10ad
        override fun toString() = "Loading"
    }
    data class Ready<T>(val value: T) : Async<T>
    data class Error(val message: String, val throwable: Throwable? = null) : Async<Nothing>

    val data: T? get() = (this as? Ready<T>)?.value
    val isLoading: Boolean get() = this is Loading

    companion object {
        val loading: Async<Nothing> = Loading()
    }
}

/** A tiny holder that lets ViewModels express "loading / ready / failed" without boilerplate. */
class AsyncValue<T>(initial: Async<T> = Async.Idle) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<Async<T>> = _state.asStateFlow()

    fun set(value: Async<T>) { _state.value = value }
    fun loading() { _state.value = Async.loading }
    fun ready(value: T) { _state.value = Async.Ready(value) }
    fun fail(message: String, t: Throwable? = null) { _state.value = Async.Error(message, t) }

    suspend fun load(block: suspend () -> T) {
        loading()
        runCatching { block() }
            .onSuccess { ready(it) }
            .onFailure { fail(it.message ?: "Something went wrong", it) }
    }
}

/** Runs a one-shot side effect keyed by [key] — for screen entry loads. */
@Composable
fun RunOnce(key: Any?, block: suspend () -> Unit) {
    LaunchedEffect(key) { block() }
}

/** Simple debounce for search fields. */
@Composable
fun rememberDebounced(value: String, delayMs: Long = 320): String {
    var debounced by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        kotlinx.coroutines.delay(delayMs)
        debounced = value
    }
    return debounced
}
