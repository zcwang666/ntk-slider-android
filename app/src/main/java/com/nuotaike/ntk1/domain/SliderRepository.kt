package com.nuotaike.ntk1.domain

import com.nuotaike.ntk1.ble.BleClient
import com.nuotaike.ntk1.ble.ConnectionState
import com.nuotaike.ntk1.protocol.SliderCommand
import com.nuotaike.ntk1.protocol.SliderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 业务层：把 BLE 上报汇聚成唯一的界面状态，并转发用户操作。
 *
 * 关键约束：所有状态都来源于设备上报；断开连接时强制清空，
 * 避免出现原版"断连后界面仍显示旧数据"的问题。
 */
class SliderRepository(
    val ble: BleClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SliderState.EMPTY)
    val state: StateFlow<SliderState> = _state.asStateFlow()

    /** 设备主动要求打开设置页（上报 F5）。 */
    private val _openSetupRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val openSetupRequests: SharedFlow<Unit> = _openSetupRequests.asSharedFlow()

    /** 一次性提示（例如"连接成功""指令失败"）。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        scope.launch {
            ble.telemetry.collect { update ->
                _state.update { it.reduce(update) }
                if (update.requestOpenSetup) _openSetupRequests.tryEmit(Unit)
                if (update.firstRun == true) _messages.tryEmit("控制器首次运行，蓝牙初始化成功")
            }
        }
        scope.launch {
            ble.connectionState.collect { connection ->
                when (connection) {
                    ConnectionState.READY -> _messages.tryEmit("已连接 ${ble.connectedName.value ?: ""}")
                    ConnectionState.IDLE, ConnectionState.FAILED -> _state.value = SliderState.EMPTY
                    else -> Unit
                }
            }
        }
    }

    fun send(command: SliderCommand) = ble.send(command)

    fun sendAll(commands: List<SliderCommand>) = ble.sendAll(commands)

    /** 发送原始字节帧（联调探测用）。 */
    fun sendRaw(frame: ByteArray) = ble.sendRaw(frame)

    fun requestFullSync() = ble.requestFullSync()
}
