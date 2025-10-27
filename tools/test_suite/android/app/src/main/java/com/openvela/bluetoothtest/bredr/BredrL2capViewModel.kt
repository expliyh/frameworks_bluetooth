package com.openvela.bluetoothtest.bredr

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.openvela.bluetooth.BtSock
import com.openvela.bluetoothtest.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BredrL2capViewModel(application: Application) : AndroidViewModel(application) {

    data class ChannelUiState(
        val id: Int,
        val title: String,
        val servicePsm: String,
        val remoteAddress: String,
        val dataToSend: String,
        val isRegistered: Boolean,
        val isConnected: Boolean,
        val isSending: Boolean,
        val logEntries: List<String>
    )

    private val app: Application = getApplication()

    private val _channels = mutableStateListOf<ChannelUiState>()
    val channels: List<ChannelUiState> get() = _channels

    var selectedChannelId by mutableStateOf<Int?>(null)
        private set

    private val controllers = mutableMapOf<Int, L2capController>()
    private val logBuffers = mutableMapOf<Int, LogBuffer>()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var nextId = 1

    init {
        addChannel(openDetail = false)
    }

    fun addChannel(title: String? = null, openDetail: Boolean = true) {
        val id = nextId++
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: app.getString(R.string.l2cap_default_title, id)
        val channel = ChannelUiState(
            id = id,
            title = resolvedTitle,
            servicePsm = "",
            remoteAddress = "",
            dataToSend = "",
            isRegistered = false,
            isConnected = false,
            isSending = false,
            logEntries = emptyList()
        )
        _channels.add(channel)
        controllers[id] = L2capController(id, ::appendControllerLog, ::handleControllerError)
        logBuffers[id] = LogBuffer(MAX_LOG_ENTRIES)
        if (openDetail) {
            selectChannel(id)
        }
    }

    fun removeChannel(id: Int) {
        controllers.remove(id)?.cleanup()
        logBuffers.remove(id)
        val index = _channels.indexOfFirst { it.id == id }
        if (index != -1) {
            _channels.removeAt(index)
        }
        if (selectedChannelId == id) {
            selectedChannelId = null
        }
        if (_channels.isEmpty()) {
            addChannel(openDetail = false)
        }
    }

    fun resetChannels() {
        controllers.values.forEach { it.cleanup() }
        controllers.clear()
        logBuffers.clear()
        _channels.clear()
        nextId = 1
        selectedChannelId = null
        addChannel(openDetail = false)
    }

    fun selectChannel(id: Int?) {
        selectedChannelId = id
    }

    fun updateServicePsm(id: Int, value: String) {
        updateChannel(id) { channel ->
            channel.copy(servicePsm = value.trim())
        }
    }

    fun updateRemoteAddress(id: Int, value: String) {
        val formatted = value.uppercase(Locale.US)
            .filter { it == ':' || it in '0'..'9' || it in 'A'..'F' }
        updateChannel(id) { channel ->
            channel.copy(remoteAddress = formatted)
        }
    }

    fun updateDataToSend(id: Int, value: String) {
        updateChannel(id) { channel ->
            channel.copy(dataToSend = value)
        }
    }

    fun registerServer(id: Int) {
        val channel = findChannel(id) ?: return
        val psm = channel.servicePsm.ifBlank { "-" }
        appendControllerLog(id, app.getString(R.string.l2cap_log_request_register, psm))
        controllers[id]?.register(channel.servicePsm)
        updateChannel(id) { it.copy(isRegistered = true) }
    }

    fun unregisterServer(id: Int) {
        appendControllerLog(id, app.getString(R.string.l2cap_log_request_unregister))
        controllers[id]?.unregister()
        updateChannel(id) { it.copy(isRegistered = false) }
    }

    fun connect(id: Int) {
        val channel = findChannel(id) ?: return
        appendControllerLog(
            id,
            app.getString(
                R.string.l2cap_log_request_connect,
                channel.remoteAddress.ifBlank { "-" },
                channel.servicePsm.ifBlank { "-" }
            )
        )
        controllers[id]?.connect(channel.remoteAddress, channel.servicePsm)
        updateChannel(id) { it.copy(isConnected = true) }
    }

    fun disconnect(id: Int) {
        appendControllerLog(id, app.getString(R.string.l2cap_log_request_disconnect))
        controllers[id]?.disconnect()
        updateChannel(id) { it.copy(isConnected = false) }
    }

    fun send(id: Int) {
        val channel = findChannel(id) ?: return
        val payload = channel.dataToSend
        appendControllerLog(
            id,
            app.getString(R.string.l2cap_log_request_send, payload.length)
        )
        updateChannel(id) { it.copy(isSending = true) }
        controllers[id]?.send(payload)
        updateChannel(id) { it.copy(isSending = false) }
    }

    fun clearLog(id: Int) {
        logBuffers[id]?.clear()
        updateChannel(id) { it.copy(logEntries = emptyList()) }
    }

    override fun onCleared() {
        controllers.values.forEach { it.cleanup() }
        controllers.clear()
        logBuffers.clear()
        super.onCleared()
    }

    private fun updateChannel(id: Int, transform: (ChannelUiState) -> ChannelUiState) {
        val index = _channels.indexOfFirst { it.id == id }
        if (index != -1) {
            _channels[index] = transform(_channels[index])
        }
    }

    private fun findChannel(id: Int): ChannelUiState? {
        return _channels.firstOrNull { it.id == id }
    }

    private fun appendControllerLog(id: Int, message: String) {
        val title = findChannel(id)?.title ?: app.getString(R.string.l2cap_default_title, id)
        appendLog(id, "$title: $message")
    }

    private fun handleControllerError(id: Int, error: String) {
        appendLog(id, app.getString(R.string.l2cap_log_error, error))
    }

    private fun appendLog(id: Int, message: String) {
        val timestamp = synchronized(timeFormatter) {
            timeFormatter.format(Date())
        }
        val entry = "[$timestamp] $message"
        val buffer = logBuffers.getOrPut(id) { LogBuffer(MAX_LOG_ENTRIES) }
        val snapshot = buffer.append(entry)
        updateChannel(id) { channel ->
            channel.copy(logEntries = snapshot)
        }
    }

    private inner class L2capController(
        private val id: Int,
        private val log: (Int, String) -> Unit,
        private val onError: (Int, String) -> Unit
    ) {
        private val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == BtSock.MESSAGE_SOCK_LOGGING) {
                    val output = msg.data?.getString("log").orEmpty()
                    if (output.isNotEmpty()) {
                        log(id, output)
                    }
                }
            }
        }

        private val btSock = BtSock(id, BtSock.SOCK_TYPE_L2CAP_BREDR_INSECURE, handler)

        fun register(psm: String) = safeCall { btSock.register(psm) }
        fun unregister() = safeCall { btSock.unregister() }
        fun connect(address: String, psm: String) = safeCall { btSock.connect(address, psm) }
        fun disconnect() = safeCall { btSock.disconnect() }
        fun send(data: String) = safeCall { btSock.send(data, 0) }

        fun cleanup() {
            safeCall { btSock.disconnect() }
            safeCall { btSock.unregister() }
        }

        private fun safeCall(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                onError(id, t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private class LogBuffer(
        private val capacity: Int
    ) {
        private val entries = ArrayDeque<String>()

        fun append(entry: String): List<String> {
            entries.addFirst(entry)
            while (entries.size > capacity) {
                entries.removeLast()
            }
            return entries.toList()
        }

        fun clear() {
            entries.clear()
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 400
    }
}

