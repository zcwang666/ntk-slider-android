package com.nuotaike.ntk1.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.nuotaike.ntk1.protocol.FrameCodec
import com.nuotaike.ntk1.protocol.SliderCommand
import com.nuotaike.ntk1.protocol.TelemetryParser
import com.nuotaike.ntk1.protocol.TelemetryUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * BLE 通信层。
 *
 * 与原版小程序的差异（均为修复原版缺陷）：
 * 1. 握手帧只在服务发现完成后发送**一次**，不再对每个特征重复发送；
 * 2. 通知监听只注册一次，重连不会叠加监听器；
 * 3. 接收缓冲按字节切帧，不完整帧保留等待，不再错位解析或无限增长；
 * 4. 写入串行化 + 超时 + 重试，消除"连续点按丢包"；
 * 5. 掉线后指数退避自动重连，并有明确的状态机。
 */
@SuppressLint("MissingPermission")
class BleClient(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {

    companion object {
        /** logcat 标签，联调时用 `adb logcat -s NtkBle` 只看本 App 的蓝牙日志。 */
        const val TAG = "NtkBle"

        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** 标准 GAP 服务（Generic Access）。 */
        private val GAP_SERVICE: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")

        /** 标准 GATT 服务（Generic Attribute）。 */
        private val GATT_SERVICE: UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")

        /** 指令之间的最小间隔，避免 BLE 栈丢弃连续写入。 */
        private const val WRITE_GAP_MS = 20L

        /** 单条写入的等待上限。 */
        private const val WRITE_TIMEOUT_MS = 1_500L

        private const val WRITE_RETRIES = 2
        private const val MAX_LOG_ENTRIES = 200
        private const val RECONNECT_BASE_MS = 1_000L
        private const val RECONNECT_MAX_MS = 30_000L
        private const val RX_BUFFER_LIMIT = 512
    }

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _logs = MutableStateFlow<List<LinkLog>>(emptyList())
    val logs: StateFlow<List<LinkLog>> = _logs.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    private val _telemetry = MutableSharedFlow<TelemetryUpdate>(extraBufferCapacity = 128)
    val telemetry: SharedFlow<TelemetryUpdate> = _telemetry.asSharedFlow()

    private val deviceByAddress = mutableMapOf<String, BluetoothDevice>()

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private var rxBuffer = ByteArray(0)
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    private val txQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var txPump: Job? = null

    private var connectedAddress: String? = null
    private var desiredConnection = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var scanning = false

    /** 需要弹给用户看的提示（与仓库层的 messages 一起被界面收集）。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // ---------------------------------------------------------------- 权限

    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------- 适配器

    private fun adapter(): BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isBluetoothSupported(): Boolean = adapter() != null

    fun isBluetoothEnabled(): Boolean = try {
        adapter()?.isEnabled == true
    } catch (t: SecurityException) {
        false
    }

    // ---------------------------------------------------------------- 扫描

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            log(LinkLog.Direction.ERROR, "扫描失败，错误码 $errorCode")
            scanning = false
            _connectionState.value = ConnectionState.IDLE
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val name = try {
            result.scanRecord?.deviceName ?: device.name
        } catch (t: SecurityException) {
            null
        }
        deviceByAddress[device.address] = device

        val entry = DiscoveredDevice(
            address = device.address,
            name = DeviceFilter.displayName(name),
            rssi = result.rssi,
            lastSeen = System.currentTimeMillis(),
            matchesTarget = DeviceFilter.matches(name),
        )

        // 联调时把每个广播都打出来，便于确认滑轨到底用什么名字广播
        Log.d(TAG, "SCAN ${device.address} rssi=${result.rssi} name=${name ?: "(无)"} 匹配=${entry.matchesTarget}")

        _devices.update { current ->
            val next = current.filterNot { it.address == entry.address } + entry
            next.sortedWith(
                compareByDescending<DiscoveredDevice> { it.matchesTarget }
                    .thenByDescending { it.rssi },
            )
        }
    }

    fun startScan() {
        if (!hasPermissions()) {
            log(LinkLog.Direction.ERROR, "缺少蓝牙权限，无法扫描")
            return
        }
        val adapter = adapter() ?: run {
            log(LinkLog.Direction.ERROR, "此设备不支持蓝牙")
            return
        }
        if (!adapter.isEnabled) {
            log(LinkLog.Direction.ERROR, "蓝牙未开启")
            _connectionState.value = ConnectionState.IDLE
            return
        }
        // 扫描与连接互斥：真机上连上滑轨后再扫描会干扰 GATT 链路，
        // 导致滑轨掉线、状态机卡死（只能杀进程恢复）。这里直接拒绝。
        if (gatt != null || _connectionState.value == ConnectionState.READY ||
            _connectionState.value == ConnectionState.RECONNECTING
        ) {
            log(LinkLog.Direction.ERROR, "已连接滑轨，不能同时扫描")
            _messages.tryEmit("已连接滑轨，请先断开连接再扫描")
            return
        }
        if (scanning) return

        val scanner = adapter.bluetoothLeScanner ?: run {
            log(LinkLog.Direction.ERROR, "蓝牙扫描器不可用")
            return
        }

        _devices.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            _connectionState.value = ConnectionState.SCANNING
            log(LinkLog.Direction.INFO, "开始扫描滑轨…")
        } catch (t: Throwable) {
            log(LinkLog.Direction.ERROR, "启动扫描失败：${t.message}")
        }
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (t: Throwable) {
            // 忽略：扫描可能已经因蓝牙关闭而终止
        }
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = if (gatt != null) ConnectionState.READY else ConnectionState.IDLE
        }
    }

    // ---------------------------------------------------------------- 连接

    fun connect(address: String) {
        if (!hasPermissions()) {
            log(LinkLog.Direction.ERROR, "缺少蓝牙权限，无法连接")
            return
        }
        val adapter = adapter() ?: return
        val device = deviceByAddress[address] ?: try {
            adapter.getRemoteDevice(address)
        } catch (t: IllegalArgumentException) {
            log(LinkLog.Direction.ERROR, "设备地址无效：$address")
            return
        }

        stopScan()
        closeGatt()

        desiredConnection = true
        connectedAddress = address
        _connectedName.value = try {
            DeviceFilter.displayName(device.name)
        } catch (t: SecurityException) {
            "NTK-1"
        }
        _connectionState.value = ConnectionState.CONNECTING
        log(LinkLog.Direction.INFO, "正在连接 $_connectedName…")

        gatt = try {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (t: Throwable) {
            log(LinkLog.Direction.ERROR, "建立连接失败：${t.message}")
            _connectionState.value = ConnectionState.FAILED
            null
        }
    }

    fun disconnect() {
        desiredConnection = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        stopScan()
        closeGatt()
        _connectionState.value = ConnectionState.IDLE
        _connectedName.value = null
        log(LinkLog.Direction.INFO, "已断开连接")
    }

    /** 兜底复位：扫描/连接卡死时把链路彻底清干净，不必杀进程。 */
    fun forceReset() {
        desiredConnection = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        stopScan()
        closeGatt()
        _devices.value = emptyList()
        deviceByAddress.clear()
        _connectionState.value = ConnectionState.IDLE
        _connectedName.value = null
        log(LinkLog.Direction.INFO, "链路已复位")
        _messages.tryEmit("链路已复位，可以重新扫描")
    }

    private fun closeGatt() {
        txPump?.cancel()
        txPump = null
        pendingWrite = null
        writeCharacteristic = null
        notifyCharacteristic = null
        rxBuffer = ByteArray(0)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (t: Throwable) {
            // 忽略关闭异常
        }
        gatt = null
    }

    private fun scheduleReconnect() {
        val address = connectedAddress ?: return
        if (!desiredConnection) {
            _connectionState.value = ConnectionState.IDLE
            return
        }
        reconnectJob?.cancel()
        reconnectAttempt++
        val backoff = (RECONNECT_BASE_MS shl (reconnectAttempt - 1).coerceAtMost(5))
            .coerceAtMost(RECONNECT_MAX_MS)
        _connectionState.value = ConnectionState.RECONNECTING
        log(LinkLog.Direction.INFO, "${backoff / 1000} 秒后自动重连（第 $reconnectAttempt 次）")
        reconnectJob = scope.launch {
            delay(backoff)
            if (desiredConnection && connectedAddress == address) connect(address)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempt = 0
                    _connectionState.value = ConnectionState.DISCOVERING
                    log(LinkLog.Direction.INFO, "已连接，正在发现服务…")
                    try {
                        g.discoverServices()
                    } catch (t: Throwable) {
                        log(LinkLog.Direction.ERROR, "发现服务失败：${t.message}")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    log(LinkLog.Direction.INFO, "链路断开（status=$status）")
                    closeGatt()
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(LinkLog.Direction.ERROR, "服务发现失败（status=$status）")
                _connectionState.value = ConnectionState.FAILED
                return
            }
            if (!resolveCharacteristics(g)) {
                log(LinkLog.Direction.ERROR, "未找到可写/可通知特征，可能不是 NTK-1 设备")
                _connectionState.value = ConnectionState.FAILED
                return
            }
            notifyCharacteristic?.let { enableNotification(g, it) }
            startTxPump()

            // 与原版一致：握手 → 拉取全部状态。只发一次。
            txQueue.trySend(SliderCommand.HANDSHAKE.frame)
            txQueue.trySend(SliderCommand.QUERY_ALL.frame)

            _connectionState.value = ConnectionState.READY
            log(LinkLog.Direction.INFO, "就绪，已请求全量状态")
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            handleIncoming(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    /**
     * 挑选可写与可通知特征。优先在同一主服务内配对，找不到再跨服务兜底。
     * 与原版一致：不做 UUID 硬编码（JDY-19 模组的 UUID 随批次可能不同）。
     */
    private fun resolveCharacteristics(g: BluetoothGatt): Boolean {
        var fallbackWrite: BluetoothGattCharacteristic? = null
        var fallbackNotify: BluetoothGattCharacteristic? = null

        for (service in g.services) {
            // 跳过标准 GAP / GATT 服务，它们不承载滑轨指令
            if (service.uuid == GAP_SERVICE || service.uuid == GATT_SERVICE) continue

            var serviceWrite: BluetoothGattCharacteristic? = null
            var serviceNotify: BluetoothGattCharacteristic? = null

            for (c in service.characteristics) {
                val p = c.properties
                if (serviceWrite == null &&
                    (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                ) {
                    serviceWrite = c
                }
                if (serviceNotify == null &&
                    (p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                ) {
                    serviceNotify = c
                }
            }

            if (serviceWrite != null && serviceNotify != null) {
                writeCharacteristic = serviceWrite
                notifyCharacteristic = serviceNotify
                log(
                    LinkLog.Direction.INFO,
                    "服务 ${service.uuid}：写=${serviceWrite.uuid}(0x%02X) 通知=${serviceNotify.uuid}"
                        .format(serviceWrite.properties),
                )
                return true
            }
            if (fallbackWrite == null) fallbackWrite = serviceWrite
            if (fallbackNotify == null) fallbackNotify = serviceNotify
        }

        writeCharacteristic = fallbackWrite
        notifyCharacteristic = fallbackNotify ?: fallbackWrite
        return writeCharacteristic != null
    }

    private fun enableNotification(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        try {
            if (!g.setCharacteristicNotification(c, true)) {
                log(LinkLog.Direction.ERROR, "开启通知失败")
                return
            }
            val cccd = c.getDescriptor(CCCD_UUID) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        } catch (t: Throwable) {
            log(LinkLog.Direction.ERROR, "开启通知异常：${t.message}")
        }
    }

    // ---------------------------------------------------------------- 接收

    private fun handleIncoming(chunk: ByteArray) {
        val merged = rxBuffer + chunk
        val result = FrameCodec.extract(merged)
        // 缓冲上限保护：异常设备持续推送垃圾数据时也不无限增长
        rxBuffer = if (result.remainder.size > RX_BUFFER_LIMIT) {
            log(LinkLog.Direction.ERROR, "接收缓冲溢出，已重置")
            ByteArray(0)
        } else {
            result.remainder
        }

        result.frames.forEach { frame ->
            log(LinkLog.Direction.RX, FrameCodec.hexCompact(frame))
            TelemetryParser.parse(frame)?.let { _telemetry.tryEmit(it) }
        }
    }

    // ---------------------------------------------------------------- 发送

    private fun startTxPump() {
        txPump?.cancel()
        txPump = scope.launch(Dispatchers.IO) {
            for (frame in txQueue) {
                val g = gatt ?: continue
                val c = writeCharacteristic ?: continue

                var ok = false
                var attempt = 0
                while (!ok && attempt <= WRITE_RETRIES) {
                    ok = writeAndAwait(g, c, frame)
                    if (!ok) {
                        attempt++
                        if (attempt <= WRITE_RETRIES) delay(WRITE_GAP_MS * 2)
                    }
                }
                log(
                    if (ok) LinkLog.Direction.TX else LinkLog.Direction.ERROR,
                    FrameCodec.hexCompact(frame) + if (ok) "" else "  ✕ 发送失败",
                )
                delay(WRITE_GAP_MS)
            }
        }
    }

    private suspend fun writeAndAwait(
        g: BluetoothGatt,
        c: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingWrite = deferred

        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.value = value
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    g.writeCharacteristic(c)
                }
            }
        } catch (t: Throwable) {
            log(LinkLog.Direction.ERROR, "写入异常：${t.message}")
            false
        }

        if (!started) {
            pendingWrite = null
            return false
        }

        return try {
            withTimeout(WRITE_TIMEOUT_MS) { deferred.await() }
        } catch (t: TimeoutCancellationException) {
            false
        } finally {
            pendingWrite = null
        }
    }

    /** 下发一条指令。写入由队列串行化，不会阻塞调用方。 */
    fun send(command: SliderCommand) {
        if (_connectionState.value != ConnectionState.READY) {
            log(LinkLog.Direction.ERROR, "未就绪，忽略指令 ${command.label}")
            return
        }
        txQueue.trySend(command.frame)
    }

    /** 连续下发多条指令（场景预设用）。 */
    fun sendAll(commands: List<SliderCommand>) {
        commands.forEach { send(it) }
    }

    /**
     * 发送原始字节帧（联调用）。
     *
     * 用于探测固件里可能存在、但官方小程序未暴露的指令。
     * 不做任何校验，调用方自负其责。
     */
    fun sendRaw(frame: ByteArray) {
        if (frame.isEmpty()) return
        if (_connectionState.value != ConnectionState.READY) {
            log(LinkLog.Direction.ERROR, "未就绪，忽略原始指令")
            return
        }
        txQueue.trySend(frame)
    }

    /** 主动请求一次全量状态同步。 */
    fun requestFullSync() {
        send(SliderCommand.QUERY_ALL)
    }

    fun release() {
        desiredConnection = false
        reconnectJob?.cancel()
        stopScan()
        closeGatt()
        txQueue.close()
    }

    // ---------------------------------------------------------------- 日志

    private fun log(direction: LinkLog.Direction, text: String) {
        val entry = LinkLog(System.currentTimeMillis(), direction, text)
        _logs.update { (it + entry).takeLast(MAX_LOG_ENTRIES) }

        // 同时写 logcat，便于通过 adb 联调
        when (direction) {
            LinkLog.Direction.ERROR -> Log.e(TAG, text)
            LinkLog.Direction.TX -> Log.d(TAG, "TX $text")
            LinkLog.Direction.RX -> Log.d(TAG, "RX $text")
            LinkLog.Direction.INFO -> Log.i(TAG, text)
        }
    }
}
