package com.openvela.bluetoothtest.bredr.spp

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.openvela.bluetooth.BtSock
import java.util.UUID
import kotlin.collections.ArrayDeque

class SppViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val LOG_MAX_CHARS = 10_000
        private const val DEFAULT_TITLE_PREFIX = "SPP #"
        private const val LOG_ENTRY_DELIMITER = "\u001E"
        private const val LINE_SEPARATOR = "\n"
        const val DEFAULT_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    }

    data class SppSessionUiState(
        val id: Int,
        val title: String,
        val serviceUuid: String,
        val remoteAddress: String,
        val dataToSend: String,
        val cycles: String,
        val logEntries: List<String>,
        val isRegistered: Boolean,
        val isConnected: Boolean,
        val isSending: Boolean,
        val logRawDataEnabled: Boolean
    )

    data class BondedDeviceOption(
        val name: String,
        val address: String,
        val isConnected: Boolean
    )

    private val store = SppSessionStore(application.applicationContext)
    private val bluetoothManager: BluetoothManager? =
        application.getSystemService(BluetoothManager::class.java)

    private val _sessions = mutableStateListOf<SppSessionUiState>()
    val sessions: List<SppSessionUiState> get() = _sessions

    var selectedSessionId by mutableStateOf<Int?>(null)
        private set

    private val controllers = mutableMapOf<Int, SppController>()
    private val logBuffers = mutableMapOf<Int, LogBuffer>()
    private var nextId = 1

    init {
        restoreSessions()
    }

    private fun restoreSessions() {
        val restored = store.loadSessions()
        if (restored.isNotEmpty()) {
            restored.forEach { record ->
                val session = record.toUiState()
                _sessions.add(session)
                controllers[session.id] = SppController(session.id, ::appendLog)
                logBuffers[session.id] = LogBuffer(LOG_MAX_CHARS, session.logEntries)
            }
            nextId = restored.maxOf { it.id } + 1
        } else {
            addSession(openDetail = false)
        }
    }

    fun addSession(title: String? = null, serviceUuid: String? = null, openDetail: Boolean = true) {
        val id = nextId++
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "$DEFAULT_TITLE_PREFIX$id"
        val resolvedUuid = serviceUuid?.takeIf { it.isNotBlank() } ?: DEFAULT_UUID
        val session = SppSessionUiState(
            id = id,
            title = resolvedTitle,
            serviceUuid = resolvedUuid,
            remoteAddress = "",
            dataToSend = "",
            cycles = "1",
            logEntries = emptyList(),
            isRegistered = false,
            isConnected = false,
            isSending = false,
            logRawDataEnabled = false
        )
        _sessions.add(session)
        controllers[id] = SppController(id, ::appendLog)
        logBuffers[id] = LogBuffer(LOG_MAX_CHARS)
        persistSession(session)
        if (openDetail) {
            selectSession(id)
        }
    }

    fun removeSession(id: Int) {
        controllers.remove(id)?.cleanup()
        logBuffers.remove(id)
        val index = _sessions.indexOfFirst { it.id == id }
        if (index != -1) {
            _sessions.removeAt(index)
            store.delete(id)
        }
        if (selectedSessionId == id) {
            selectedSessionId = null
        }
    }

    fun resetSessions() {
        controllers.values.forEach { it.cleanup() }
        controllers.clear()
        logBuffers.clear()
        _sessions.clear()
        store.clear()
        nextId = 1
        addSession(openDetail = false)
        selectSession(null)
    }

    fun selectSession(id: Int?) {
        selectedSessionId = id
    }

    fun updateServiceUuid(id: Int, value: String) {
        updateSession(id, persist = true) { it.copy(serviceUuid = value) }
    }

    fun updateRemoteAddress(id: Int, value: String) {
        updateSession(id, persist = true) { it.copy(remoteAddress = value) }
    }

    fun updateDataToSend(id: Int, value: String) {
        updateSession(id, persist = true) { it.copy(dataToSend = value) }
    }

    fun updateCycles(id: Int, value: String) {
        updateSession(id, persist = true) { it.copy(cycles = value) }
    }

    fun clearLog(id: Int) {
        logBuffers.getOrPut(id) { LogBuffer(LOG_MAX_CHARS) }.clear()
        updateSession(id, persist = true) { it.copy(logEntries = emptyList()) }
    }

    fun setRawLogEnabled(id: Int, enabled: Boolean) {
        controllers[id]?.setRawLogging(enabled)
        updateSession(id, persist = false) { it.copy(logRawDataEnabled = enabled) }
    }

    fun registerServer(id: Int) {
        val session = findSession(id) ?: return
        val uuid = session.serviceUuid.trim()
        if (!isValidUuid(uuid)) {
            appendLog(id, "Invalid UUID: $uuid$LINE_SEPARATOR")
            return
        }
        controllers[id]?.register(uuid)
        updateSession(id, persist = false) { it.copy(isRegistered = true) }
    }

    fun unregisterServer(id: Int) {
        controllers[id]?.unregister()
        updateSession(id, persist = false) { it.copy(isRegistered = false) }
    }

    fun connect(id: Int) {
        val session = findSession(id) ?: return
        val uuid = session.serviceUuid.trim()
        val address = session.remoteAddress.trim()
        if (!isValidUuid(uuid)) {
            appendLog(id, "Invalid UUID: $uuid$LINE_SEPARATOR")
            return
        }
        if (address.isEmpty()) {
            appendLog(id, "Remote address is empty$LINE_SEPARATOR")
            return
        }
        controllers[id]?.connect(address, uuid)
        updateSession(id, persist = false) { it.copy(isConnected = true) }
    }

    fun disconnect(id: Int) {
        controllers[id]?.disconnect()
        updateSession(id, persist = false) { it.copy(isConnected = false) }
    }

    fun send(id: Int) {
        val session = findSession(id) ?: return
        val data = session.dataToSend
        val cycles = session.cycles.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
        if (data.isEmpty()) {
            appendLog(id, "Nothing to send$LINE_SEPARATOR")
            return
        }
        if (cycles <= 0) {
            appendLog(id, "Cycles must be > 0$LINE_SEPARATOR")
            return
        }
        updateSession(id, persist = false) { it.copy(isSending = true) }
        controllers[id]?.send(data, cycles)
        updateSession(id, persist = false) { it.copy(isSending = false) }
    }

    private fun findSession(id: Int): SppSessionUiState? = _sessions.firstOrNull { it.id == id }

    private fun updateSession(
        id: Int,
        persist: Boolean,
        transform: (SppSessionUiState) -> SppSessionUiState
    ) {
        val index = _sessions.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = transform(_sessions[index])
            _sessions[index] = updated
            if (persist) {
                persistSession(updated)
            }
        }
    }

    private fun appendLog(id: Int, message: String) {
        val normalized = normalizeLogEntry(message)
        val buffer = logBuffers.getOrPut(id) { LogBuffer(LOG_MAX_CHARS) }
        val snapshot = buffer.append(normalized)
        updateSession(id, persist = false) { state ->
            state.copy(logEntries = snapshot)
        }
    }

    private fun persistSession(session: SppSessionUiState) {
        val logString = logBuffers[session.id]?.persistedString()
            ?: joinLogEntries(session.logEntries)
        store.upsert(session.toRecord(logString))
    }

    private fun joinLogEntries(entries: List<String>): String =
        if (entries.isEmpty()) "" else entries.joinToString(separator = LOG_ENTRY_DELIMITER) {
            it.normalizeLineEndings()
        }

    private fun String.toLogEntries(): List<String> {
        if (isBlank()) return emptyList()
        return if (contains(LOG_ENTRY_DELIMITER)) {
            split(LOG_ENTRY_DELIMITER).mapNotNull { entry ->
                val normalized = entry.normalizeLineEndings()
                if (normalized.isEmpty()) null else normalized
            }
        } else {
            val normalized = normalizeLineEndings()
            if (normalized.isEmpty()) emptyList() else listOf(normalized)
        }
    }

    private fun String.normalizeLineEndings(): String =
        replace("\r\n", LINE_SEPARATOR).replace("\r", LINE_SEPARATOR)

    private fun normalizeLogEntry(message: String): String {
        val normalized = message.normalizeLineEndings()
        return if (normalized.endsWith(LINE_SEPARATOR)) normalized else normalized + LINE_SEPARATOR
    }

    private inner class LogBuffer(
        private val maxChars: Int,
        initialEntries: List<String> = emptyList()
    ) {
        private val entries = ArrayDeque<String>()
        private var totalChars = 0

        init {
            initialEntries.asReversed().forEach { entry ->
                if (entry.isNotEmpty()) {
                    entries.addFirst(entry)
                    totalChars += entry.length
                }
            }
            trimTail()
        }

        fun append(entry: String): List<String> {
            if (entry.isEmpty()) {
                return snapshot()
            }
            entries.addFirst(entry)
            totalChars += entry.length
            trimTail()
            return snapshot()
        }

        fun clear() {
            entries.clear()
            totalChars = 0
        }

        fun persistedString(): String =
            if (entries.isEmpty()) "" else entries.joinToString(separator = LOG_ENTRY_DELIMITER) {
                it.normalizeLineEndings()
            }

        fun snapshot(): List<String> = entries.toList()

        private fun trimTail() {
            while (totalChars > maxChars && entries.isNotEmpty()) {
                val removed = entries.removeLast()
                totalChars -= removed.length
                val remainingCapacity = maxChars - totalChars
                if (remainingCapacity > 0) {
                    val trimmed = removed.take(remainingCapacity)
                    if (trimmed.isNotEmpty()) {
                        entries.addLast(trimmed)
                        totalChars += trimmed.length
                        break
                    }
                }
            }
        }
    }

    private fun SppSessionStore.Record.toUiState() = SppSessionUiState(
        id = id,
        title = title,
        serviceUuid = serviceUuid,
        remoteAddress = remoteAddress,
        dataToSend = dataToSend,
        cycles = cycles,
        logEntries = log.toLogEntries(),
        isRegistered = false,
        isConnected = false,
        isSending = false,
        logRawDataEnabled = false
    )

    private fun SppSessionUiState.toRecord(logContent: String) = SppSessionStore.Record(
        id = id,
        title = title,
        serviceUuid = serviceUuid,
        remoteAddress = remoteAddress,
        dataToSend = dataToSend,
        cycles = cycles,
        log = logContent.take(LOG_MAX_CHARS)
    )

    private fun isValidUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun loadBondedDevices(): List<BondedDeviceOption> {
        val manager = bluetoothManager ?: return emptyList()
        val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
        val bonded: Set<BluetoothDevice> = try {
            adapter.bondedDevices
        } catch (_: SecurityException) {
            emptySet()
        }

        val connectedAddresses = collectCurrentlyConnectedAddresses(manager)

        return bonded.map { device ->
            val displayName = device.name?.takeUnless { it.isBlank() } ?: device.address
            BondedDeviceOption(
                name = displayName,
                address = device.address,
                isConnected = connectedAddresses.contains(device.address)
            )
        }.sortedWith(
            compareByDescending<BondedDeviceOption> { it.isConnected }
                .thenBy { it.name.lowercase() }
        )
    }

    @SuppressLint("MissingPermission")
    private fun collectCurrentlyConnectedAddresses(manager: BluetoothManager): Set<String> {
        val connectedAddresses = mutableSetOf<String>()
        val profiles = listOf(
            BluetoothProfile.GATT,
            BluetoothProfile.GATT_SERVER,
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            BluetoothProfile.HID_DEVICE,
        )
        profiles.forEach { profile ->
            try {
                connectedAddresses += manager.getConnectedDevices(profile)
                    .map(BluetoothDevice::getAddress)
            } catch (_: IllegalArgumentException) {
                // Profile not supported on this device; ignore.
            } catch (_: SecurityException) {
                // Missing permission; ignore.
            }
        }
        return connectedAddresses
    }

    override fun onCleared() {
        controllers.values.forEach { it.cleanup() }
        controllers.clear()
        logBuffers.clear()
        store.close()
        super.onCleared()
    }

    private inner class SppController(
        private val id: Int,
        private val log: (Int, String) -> Unit
    ) {
        private val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == BtSock.MESSAGE_SOCK_LOGGING) {
                    val str = msg.data?.getString("log").orEmpty()
                    if (str.isNotEmpty()) {
                        log(id, str)
                    }
                }
            }
        }

        private val btSock = BtSock(id, BtSock.SOCK_TYPE_SPP_INSECURE, handler)

        fun register(uuid: String) = safeCall { btSock.register(uuid) }
        fun unregister() = safeCall { btSock.unregister() }
        fun connect(address: String, uuid: String) = safeCall { btSock.connect(address, uuid) }
        fun disconnect() = safeCall { btSock.disconnect() }
        fun send(data: String, cycles: Int) = safeCall { btSock.send(data, cycles) }
        fun setRawLogging(enabled: Boolean) = safeCall { btSock.setLogRawData(enabled) }

        fun cleanup() {
            safeCall { btSock.disconnect() }
            safeCall { btSock.unregister() }
        }

        private fun safeCall(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                log(id, "Error: ${t.message ?: t.javaClass.simpleName}$LINE_SEPARATOR")
            }
        }
    }
}

