package com.openvela.bluetoothtest.bredr

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import com.openvela.bluetooth.BluetoothBondStateObserver
import com.openvela.bluetooth.callback.BluetoothBondStateCallback
import com.openvela.bluetoothtest.R
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BondViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val bluetoothSupported: Boolean = true,
        val addressInput: String = "",
        val addressError: String? = null,
        val cyclesInput: String = "",
        val cyclesError: String? = null,
        val activeCycle: CycleInfo? = null,
        val pairedDevices: List<BondedDeviceEntry> = emptyList(),
        val log: String = ""
    )

    data class CycleInfo(
        val mode: CycleMode,
        val remaining: Int
    )

    data class BondedDeviceEntry(
        val name: String,
        val address: String,
        val isConnected: Boolean
    )

    enum class CycleMode {
        Create, Remove
    }

    private data class CycleState(
        val mode: CycleMode,
        val address: String,
        var remaining: Int
    )

    private val app: Application = getApplication()
    private val applicationContext = app.applicationContext
    private val bluetoothManager: BluetoothManager? =
        applicationContext.getSystemService(BluetoothManager::class.java)
    @Suppress("DEPRECATION")
    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val observer = BluetoothBondStateObserver(applicationContext)
    private val callback = object : BluetoothBondStateCallback {
        override fun onBonded(device: BluetoothDevice) {
            val remaining = cycleState?.remaining ?: 0
            appendLog(
                app.getString(
                    R.string.bond_log_bonded,
                    device.address,
                    remaining
                )
            )
            refreshBondedDevices()
            val state = cycleState
            if (state != null && state.remaining > 0) {
                removeBondInternal(state.address)
            } else {
                finishCycle()
            }
        }

        override fun onBondRemoved(device: BluetoothDevice) {
            val remainingBefore = cycleState?.remaining ?: 0
            appendLog(
                app.getString(
                    R.string.bond_log_removed,
                    device.address,
                    remainingBefore
                )
            )
            refreshBondedDevices()
            val state = cycleState ?: run {
                finishCycle()
                return
            }
            if (state.remaining > 0) {
                createBondInternal(state.address)
                state.remaining -= 1
                if (state.remaining > 0) {
                    updateCycleInfo(state.mode, state.remaining)
                } else {
                    finishCycle()
                }
            } else {
                finishCycle()
            }
        }
    }

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _uiState = MutableStateFlow(
        UiState(
            bluetoothSupported = bluetoothAdapter != null
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var cycleState: CycleState? = null

    init {
        if (bluetoothAdapter == null) {
            appendLog(app.getString(R.string.bond_log_adapter_unavailable))
        } else {
            observer.registerReceiver(callback)
        }
        refreshBondedDevices()
    }

    override fun onCleared() {
        observer.unregisterReceiver()
        super.onCleared()
    }

    fun onAddressInputChange(value: String) {
        val formatted = value.uppercase(Locale.US)
            .filter { it == ':' || it in '0'..'9' || it in 'A'..'F' }
        _uiState.update { it.copy(addressInput = formatted, addressError = null) }
    }

    fun onCyclesInputChange(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        _uiState.update { it.copy(cyclesInput = digitsOnly, cyclesError = null) }
    }

    fun onUseDevice(address: String) {
        _uiState.update { it.copy(addressInput = address, addressError = null) }
    }

    fun onCreateBondRequested() {
        performAction(CycleMode.Create)
    }

    fun onRemoveBondRequested() {
        performAction(CycleMode.Remove)
    }

    fun onClearLog() {
        _uiState.update { it.copy(log = "") }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        val adapter = bluetoothAdapter ?: return
        val bonded = try {
            adapter.bondedDevices
        } catch (_: SecurityException) {
            emptySet<BluetoothDevice>()
        } catch (_: Throwable) {
            emptySet<BluetoothDevice>()
        }
        val connected = collectConnectedAddresses()
        val entries = bonded.map { device ->
            val displayName = device.name?.takeUnless { it.isBlank() } ?: device.address
            BondedDeviceEntry(
                name = displayName,
                address = device.address,
                isConnected = connected.contains(device.address)
            )
        }.sortedWith(
            compareByDescending<BondedDeviceEntry> { it.isConnected }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
        _uiState.update { it.copy(pairedDevices = entries) }
    }

    private fun performAction(mode: CycleMode) {
        val adapter = bluetoothAdapter ?: run {
            appendLog(app.getString(R.string.bond_log_adapter_unavailable))
            return
        }
        val address = validateAddress() ?: return
        val cycles = parseCycles() ?: return

        when (mode) {
            CycleMode.Create -> createBondInternal(address)
            CycleMode.Remove -> removeBondInternal(address)
        }

        appendLog(
            app.getString(
                when (mode) {
                    CycleMode.Create -> R.string.bond_log_request_create
                    CycleMode.Remove -> R.string.bond_log_request_remove
                },
                address,
                cycles
            )
        )

        if (cycles > 0) {
            cycleState = CycleState(mode = mode, address = address, remaining = cycles)
            updateCycleInfo(mode, cycles)
        } else {
            finishCycle()
        }
    }

    private fun validateAddress(): String? {
        val input = _uiState.value.addressInput.trim()
        if (input.isEmpty()) {
            val error = app.getString(R.string.remote_address_error_invalid)
            _uiState.update { it.copy(addressError = error) }
            return null
        }
        val pattern = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        if (!pattern.matches(input)) {
            val error = app.getString(R.string.remote_address_error_invalid)
            _uiState.update { it.copy(addressError = error) }
            return null
        }
        return input
    }

    private fun parseCycles(): Int? {
        val text = _uiState.value.cyclesInput
        if (text.isEmpty()) {
            return 0
        }
        val parsed = text.toIntOrNull()
        return if (parsed != null) {
            parsed
        } else {
            val error = app.getString(R.string.cycle_input_error)
            _uiState.update { it.copy(cyclesError = error) }
            null
        }
    }

    private fun finishCycle() {
        cycleState = null
        _uiState.update { it.copy(activeCycle = null) }
    }

    private fun updateCycleInfo(mode: CycleMode, remaining: Int) {
        _uiState.update { it.copy(activeCycle = CycleInfo(mode, remaining)) }
    }

    @SuppressLint("MissingPermission")
    private fun createBondInternal(address: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            val device = adapter.getRemoteDevice(address)
            device.createBond()
        } catch (security: SecurityException) {
            appendLog(
                app.getString(
                    R.string.bond_log_security_error,
                    security.message ?: security.javaClass.simpleName
                )
            )
        } catch (t: Throwable) {
            appendLog(
                app.getString(
                    R.string.bond_log_generic_error,
                    t.message ?: t.javaClass.simpleName
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeBondInternal(address: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            val device = adapter.getRemoteDevice(address)
            val method: Method = device.javaClass.getMethod("removeBond")
            method.isAccessible = true
            method.invoke(device)
        } catch (security: SecurityException) {
            appendLog(
                app.getString(
                    R.string.bond_log_security_error,
                    security.message ?: security.javaClass.simpleName
                )
            )
        } catch (t: Throwable) {
            appendLog(
                app.getString(
                    R.string.bond_log_generic_error,
                    t.message ?: t.javaClass.simpleName
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun collectConnectedAddresses(): Set<String> {
        val manager = bluetoothManager ?: return emptySet()
        val profiles = listOf(
            android.bluetooth.BluetoothProfile.GATT,
            android.bluetooth.BluetoothProfile.GATT_SERVER,
            android.bluetooth.BluetoothProfile.HEADSET,
            android.bluetooth.BluetoothProfile.A2DP,
            android.bluetooth.BluetoothProfile.HID_DEVICE
        )
        val addresses = mutableSetOf<String>()
        profiles.forEach { profile ->
            try {
                addresses += manager.getConnectedDevices(profile).map(BluetoothDevice::getAddress)
            } catch (_: IllegalArgumentException) {
                // Profile not supported on this device.
            } catch (_: SecurityException) {
                // Missing permission; ignore.
            }
        }
        return addresses
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

    companion object {
        private const val MAX_LOG_CHARS = 10_000
    }
}
