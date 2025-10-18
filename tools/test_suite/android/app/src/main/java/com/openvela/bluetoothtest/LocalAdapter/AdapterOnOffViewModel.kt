package com.openvela.bluetoothtest.LocalAdapter

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openvela.bluetooth.BluetoothStateObserver
import com.openvela.bluetooth.callback.BluetoothStateCallback
import com.openvela.bluetoothtest.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdapterOnOffViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val cyclesInput: String = "",
        val cyclesInputError: String? = null,
        val bluetoothSupported: Boolean = true,
        val isBluetoothOn: Boolean = false,
        val isCycling: Boolean = false,
        val cycleMode: CycleMode? = null,
        val remainingCycles: Int = 0,
        val log: String = "",
        val cycleGeneration: Int = 0
    )

    enum class CycleMode {
        StartEnable, StartDisable
    }

    sealed interface Event {
        data class RequestEnable(val generation: Int) : Event
        data class ShowMessage(val message: String) : Event
    }

    private data class CycleState(
        val mode: CycleMode,
        var remaining: Int,
        val generation: Int
    )

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)

    @Suppress("DEPRECATION")
    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val observer = BluetoothStateObserver(application.applicationContext)
    private val callback = object : BluetoothStateCallback {
        override fun onEnabled() {
            handleBluetoothEnabled()
        }

        override fun onDisabled() {
            handleBluetoothDisabled()
        }
    }

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _uiState = MutableStateFlow(
        UiState(
            bluetoothSupported = bluetoothAdapter != null,
            isBluetoothOn = bluetoothAdapter?.isEnabled == true
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private var cycleState: CycleState? = null
    private var generationCounter = 0

    init {
        if (bluetoothAdapter == null) {
            appendLog(getString(R.string.adapter_not_supported))
        } else {
            observer.registerReceiver(callback)
            appendLog(
                if (_uiState.value.isBluetoothOn) {
                    getString(R.string.log_bluetooth_enabled, 0)
                } else {
                    getString(R.string.log_bluetooth_disabled, 0)
                }
            )
        }
    }

    override fun onCleared() {
        observer.unregisterReceiver()
        super.onCleared()
    }

    fun onCyclesInputChange(value: String) {
        val filtered = value.filter { it.isDigit() }
        _uiState.update {
            it.copy(
                cyclesInput = filtered,
                cyclesInputError = null
            )
        }
    }

    fun onEnableRequested() {
        val adapter = bluetoothAdapter ?: run {
            appendLog(getString(R.string.adapter_not_supported))
            return
        }
        val cycles = parseCycles() ?: return
        startCycle(CycleMode.StartEnable, cycles)
        if (adapter.isEnabled.not() || cycles > 0) {
            requestEnable()
        } else {
            // Already enabled and no cycle requested: log current state.
            appendLog(getString(R.string.log_bluetooth_enabled, 0))
            finishCycle()
        }
    }

    fun onDisableRequested() {
        val adapter = bluetoothAdapter ?: run {
            appendLog(getString(R.string.adapter_not_supported))
            return
        }
        val cycles = parseCycles() ?: return
        startCycle(CycleMode.StartDisable, cycles)
        if (adapter.isEnabled) {
            disableBluetooth()
        } else if (cycles > 0) {
            // Adapter already disabled; begin cycle by requesting enable.
            requestEnable()
        } else {
            appendLog(getString(R.string.log_bluetooth_disabled, 0))
            finishCycle()
        }
    }

    fun onEnableIntentResult(granted: Boolean) {
        if (!granted) {
            appendLog(getString(R.string.enable_request_cancelled))
            finishCycle()
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(log = "") }
    }

    fun cancelCycle() {
        cycleState = null
        _uiState.update {
            it.copy(
                isCycling = false,
                cycleMode = null,
                remainingCycles = 0
            )
        }
    }

    fun isCurrentGeneration(generation: Int): Boolean {
        return cycleState?.generation == generation
    }

    private fun parseCycles(): Int? {
        val text = _uiState.value.cyclesInput.trim()
        if (text.isEmpty()) {
            return 0
        }
        val parsed = text.toIntOrNull()
        return if (parsed != null && parsed >= 0) {
            parsed
        } else {
            _uiState.update {
                it.copy(cyclesInputError = getString(R.string.cycle_input_error))
            }
            null
        }
    }

    private fun startCycle(mode: CycleMode, cycles: Int) {
        generationCounter += 1
        val newState = CycleState(mode = mode, remaining = cycles, generation = generationCounter)
        cycleState = newState
        _uiState.update {
            it.copy(
                isCycling = true,
                cycleMode = mode,
                remainingCycles = cycles,
                cycleGeneration = newState.generation
            )
        }
    }

    private fun finishCycle() {
        cycleState = null
        _uiState.update {
            it.copy(
                isCycling = false,
                cycleMode = null,
                remainingCycles = 0
            )
        }
    }

    private fun handleBluetoothEnabled() {
        _uiState.update { it.copy(isBluetoothOn = true) }
        val remaining = cycleState?.remaining ?: 0
        appendLog(getString(R.string.log_bluetooth_enabled, remaining))
        val state = cycleState ?: return
        if (state.remaining > 0) {
            disableBluetooth()
        } else {
            finishCycle()
        }
    }

    private fun handleBluetoothDisabled() {
        _uiState.update { it.copy(isBluetoothOn = false) }
        val remaining = cycleState?.remaining ?: 0
        appendLog(getString(R.string.log_bluetooth_disabled, remaining))
        val state = cycleState ?: return finishCycle()
        if (state.remaining > 0) {
            requestEnable()
            state.remaining -= 1
            _uiState.update { it.copy(remainingCycles = state.remaining) }
        } else {
            finishCycle()
        }
    }

    private fun requestEnable() {
        val state = cycleState ?: return
        viewModelScope.launch {
            _events.emit(Event.RequestEnable(state.generation))
        }
    }

    private fun disableBluetooth() {
        val adapter = bluetoothAdapter ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                adapter.disable()
            } catch (_: SecurityException) {
                false
            } catch (_: Throwable) {
                false
            }
            if (!success) {
                withContext(Dispatchers.Main) {
                    handleDisableNotAllowed()
                }
            }
        }
    }

    private suspend fun handleDisableNotAllowed() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getString(R.string.disable_not_allowed_message_android_13)
        } else {
            getString(R.string.disable_not_allowed_message_legacy)
        }
        appendLog(message)
        finishCycle()
        _events.emit(Event.ShowMessage(message))
    }

    private fun appendLog(message: String) {
        val timestamp = synchronized(timeFormatter) {
            timeFormatter.format(Date())
        }
        val entry = "[$timestamp] $message"
        _uiState.update { state ->
            val combined = if (state.log.isEmpty()) {
                entry
            } else {
                "$entry\n${state.log}"
            }
            state.copy(log = combined.take(MAX_LOG_CHARS))
        }
    }

    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    companion object {
        private const val MAX_LOG_CHARS = 10_000
    }
}
