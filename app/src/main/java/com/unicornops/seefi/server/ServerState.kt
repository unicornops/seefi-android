package com.unicornops.seefi.server

import com.unicornops.seefi.data.CardConfiguration
import com.unicornops.seefi.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared state of the receive server, observed by the UI while the
 * actual server runs inside the foreground service.
 */
class ServerState(scope: CoroutineScope, settings: SettingsRepository) {

    sealed interface Status {
        data object Idle : Status
        data class Running(val port: Int) : Status
        data class Error(val message: String) : Status
    }

    data class Event(val timestamp: Long, val message: String) {
        val timeText: String
            get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
    }

    data class UiState(
        val status: Status = Status.Idle,
        val photosReceived: Int = 0,
        val events: List<Event> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val photosReceived: StateFlow<Int> get() = _photoCount

    private val _photoCount = MutableStateFlow(0)
    private val cards = MutableStateFlow<List<CardConfiguration>>(emptyList())

    init {
        scope.launch {
            settings.cardConfigurations.collect { cards.value = it }
        }
    }

    /** Synchronous snapshot of configured cards for the server threads. */
    fun cardsSnapshot(): List<CardConfiguration> = cards.value

    fun onServerStarted(port: Int) {
        _uiState.value = _uiState.value.copy(status = Status.Running(port))
        addEvent("Server listening on port $port")
    }

    fun onServerStopped() {
        _uiState.value = _uiState.value.copy(status = Status.Idle)
        addEvent("Server stopped")
    }

    fun onServerError(message: String) {
        _uiState.value = _uiState.value.copy(status = Status.Error(message))
        addEvent("Server error: $message")
    }

    fun onPhotosSaved(count: Int, fromFile: String) {
        val newCount = _photoCount.value + count
        _photoCount.value = newCount
        _uiState.value = _uiState.value.copy(photosReceived = newCount)
        addEvent("Received $count photo(s) from $fromFile")
    }

    fun addEvent(message: String) {
        val event = Event(System.currentTimeMillis(), message)
        _uiState.value = _uiState.value.copy(
            events = (listOf(event) + _uiState.value.events).take(MAX_EVENTS),
        )
    }

    companion object {
        private const val MAX_EVENTS = 50
    }
}
