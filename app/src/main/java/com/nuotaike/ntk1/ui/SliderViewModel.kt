package com.nuotaike.ntk1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nuotaike.ntk1.AppContainer
import com.nuotaike.ntk1.ble.ConnectionState
import com.nuotaike.ntk1.ble.DiscoveredDevice
import com.nuotaike.ntk1.ble.LinkLog
import com.nuotaike.ntk1.domain.PhotoTargetPlanner
import com.nuotaike.ntk1.domain.SliderRepository
import com.nuotaike.ntk1.domain.TimerPlanner
import com.nuotaike.ntk1.protocol.SliderCommand
import com.nuotaike.ntk1.protocol.SliderState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** 定时器时/分/秒。 */
data class TimerDraft(val hours: Int, val minutes: Int, val seconds: Int) {

    fun toSeconds(): Int = hours * 3600 + minutes * 60 + seconds

    companion object {
        /** 上限：99:59:59。超界返回 null。 */
        fun fromSeconds(total: Int): TimerDraft? {
            if (total < 0) return null
            val h = total / 3600
            if (h > TimerPlanner.MAX_HOURS) return null
            return TimerDraft(h, (total % 3600) / 60, total % 60)
        }
    }
}

/** 定时器可调单位。 */
enum class TimerUnit { HOUR, MINUTE, SECOND }

class SliderViewModel(
    private val repository: SliderRepository,
) : ViewModel() {

    val state: StateFlow<SliderState> = repository.state
    val connection: StateFlow<ConnectionState> = repository.ble.connectionState
    val devices: StateFlow<List<DiscoveredDevice>> = repository.ble.devices
    val logs: StateFlow<List<LinkLog>> = repository.ble.logs
    val connectedName: StateFlow<String?> = repository.ble.connectedName

    /** 本层自己产生的提示（预览定时回执等）。 */
    private val _localMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val messages = merge(repository.messages, repository.ble.messages, _localMessages)

    // ---------------------------------------------------------------- 运行意图

    private val _runIntent = MutableStateFlow(false)
    val runIntent: StateFlow<Boolean> = _runIntent.asStateFlow()

    // ---------------------------------------------------------------- 行程标定模式

    /**
     * 行程标定模式（官方界面里那个「←M→」开关）。
     *
     * 真机验证结论：**方向控制必须先进这个模式**，否则设备不响应任何方向指令。
     * 界面状态以**设备回报的 F2** 为准——本地只先做一次乐观切换，收到上报后立即对齐，
     * 避免出现"App 显示已关闭、设备其实还开着"的不同步。
     */
    private val _fineTune = MutableStateFlow(false)
    val fineTune: StateFlow<Boolean> = _fineTune.asStateFlow()

    init {
        // 只在设备真的上报 F2 时才覆盖，避免设备不上报该字段时把开关锁死
        viewModelScope.launch {
            repository.ble.telemetry
                .mapNotNull { it.setupLocked }
                .collect { _fineTune.value = it }
        }
    }

    fun setFineTune(on: Boolean) {
        if (_fineTune.value == on) return
        _fineTune.value = on
        repository.send(
            if (on) SliderCommand.FINE_TUNE_ENTER else SliderCommand.FINE_TUNE_EXIT,
        )
    }

    fun toggleFineTune() = setFineTune(!_fineTune.value)

    // ---------------------------------------------------------------- 速度

    /**
     * 速度微调。
     *
     * 真机实测结论：`B2B6` / `B8BC` 在这台滑轨上就是**调速**（每次约 ±0.5%），
     * 官方把它们标成"上升/下降"是因为别的机型带垂直轴。
     * 协议没有"直接设定速度"的指令，所以只用按键步进。
     */
    fun nudgeSpeed(up: Boolean) {
        repository.send(if (up) SliderCommand.MOVE_UP else SliderCommand.MOVE_DOWN)
    }

    // ---------------------------------------------------------------- 往返

    /**
     * 往返开关。
     *
     * 设备上报的往返次数：0 = 关闭，255 = 自动，其余为次数。
     * 协议只有 ± 步进，所以：
     * - 关闭状态 → 按一次 + 打开
     * - 已打开 → 连按若干次 − 回到 0（次数未知或"自动"时最多按 [MAX_REPEAT_STEPS] 次兜底）
     */
    fun toggleRepeat() {
        val current = state.value.repeatRaw
        if (current == 0) {
            repository.send(SliderCommand.REPEAT_INC)
            return
        }
        val steps = when {
            current == 255 -> MAX_REPEAT_STEPS
            else -> current.coerceIn(1, MAX_REPEAT_STEPS)
        }
        repeat(steps) { repository.send(SliderCommand.REPEAT_DEC) }
    }

    // ---------------------------------------------------------------- 定时器时长

    /**
     * 用户调出来的定时值（本地草稿）。
     *
     * 真机实测：**定时器停止时，设备不会把 ± 调出来的值上报回来**（C4/C6 保持旧值），
     * 所以如果文本框只跟着遥测走，用户按 ± 会看到"数字不动"。
     * 这里让 App 自己记住用户调的时/分/秒，立刻回显；
     * 点「预览定时」则丢弃草稿、向设备查一次真实值。
     */
    private val _timerDraft = MutableStateFlow<TimerDraft?>(null)
    val timerDraft: StateFlow<TimerDraft?> = _timerDraft.asStateFlow()

    /** 设备当前上报的定时值。 */
    private fun deviceTimer(): TimerDraft {
        val s = state.value
        return TimerDraft(s.timerHours ?: 0, s.timerMinutes ?: 0, s.timerSeconds ?: 0)
    }

    /** 文本框要显示的值：优先显示用户刚调的，否则显示设备的。 */
    val displayTimer: StateFlow<TimerDraft> = combine(state, _timerDraft) { st, draft ->
        draft ?: TimerDraft(st.timerHours ?: 0, st.timerMinutes ?: 0, st.timerSeconds ?: 0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimerDraft(0, 0, 0))

    /**
     * 逐格 ±。
     *
     * 按总秒数加减后重新分解，所以 59 秒再 +1 会自然进位成 1 分 0 秒
     * （闹钟该有的行为）。下发的指令是**从上一档到新档的增量**，
     * 不是从设备当前值算的——否则连按两次会重复发送。
     */
    fun nudgeTimer(unit: TimerUnit, up: Boolean) {
        val base = _timerDraft.value ?: deviceTimer()
        val step = when (unit) {
            TimerUnit.HOUR -> 3600
            TimerUnit.MINUTE -> 60
            TimerUnit.SECOND -> 1
        }
        val total = base.toSeconds() + if (up) step else -step
        val next = TimerDraft.fromSeconds(total) ?: return

        val commands = TimerPlanner.plan(
            fromHours = base.hours, toHours = next.hours,
            fromMinutes = base.minutes, toMinutes = next.minutes,
            fromSeconds = base.seconds, toSeconds = next.seconds,
        )
        _timerDraft.value = next
        if (commands.isNotEmpty()) repository.sendAll(commands)
    }

    /** 直接输入一个时/分/秒：记入草稿，并按差值补齐指令。 */
    fun setTimer(hours: Int, minutes: Int, seconds: Int) {
        val target = TimerDraft(
            hours = hours.coerceIn(0, TimerPlanner.MAX_HOURS),
            minutes = minutes.coerceIn(0, TimerPlanner.MAX_MINUTES),
            seconds = seconds.coerceIn(0, TimerPlanner.MAX_SECONDS),
        )
        val current = deviceTimer()
        val commands = TimerPlanner.plan(
            fromHours = current.hours, toHours = target.hours,
            fromMinutes = current.minutes, toMinutes = target.minutes,
            fromSeconds = current.seconds, toSeconds = target.seconds,
        )
        _timerDraft.value = target
        if (commands.isNotEmpty()) repository.sendAll(commands)
    }

    /**
     * 开关定时器。
     *
     * 真机实测：**设备只在定时器开启后才回传真实时长**，
     * 关闭状态下 C4/C6 是旧值（± 调出来的值查不回来）。
     * 所以这里点一下就把本地草稿清掉，让文本框切到设备上报的真实值。
     */
    fun toggleTimer() {
        _timerDraft.value = null
        _localMessages.tryEmit(
            if (state.value.timerOn) "定时器已关闭" else "定时器已开启，上方数字为设备实际时长",
        )
        repository.send(SliderCommand.TIMER_TOGGLE)
    }

    // ---------------------------------------------------------------- 照片目标张数

    /**
     * 把目标张数设成 [target]。
     *
     * 协议只有 ±1 / ±10 / ±100 三档预置，没有"直接设定"的指令，
     * 因此按当前上报值算出差值，拆成若干条预置指令下发。
     */
    fun setPhotoTarget(target: Int) {
        val current = state.value.photoTarget ?: return
        val commands = PhotoTargetPlanner.plan(from = current, to = target)
        if (commands.isEmpty()) return
        repository.sendAll(commands)
    }

    // ---------------------------------------------------------------- 方向控制

    /**
     * 按住方向键。
     *
     * 标定模式 → 发"按住"指令（设备持续走）
     * 普通模式 → 发单次点动指令
     */
    fun pressDirection(left: Boolean) {
        repository.send(
            when {
                _fineTune.value && left -> SliderCommand.MOVE_LEFT_HOLD
                _fineTune.value && !left -> SliderCommand.MOVE_RIGHT_HOLD
                left -> SliderCommand.MOVE_LEFT
                else -> SliderCommand.MOVE_RIGHT
            },
        )
    }

    /** 松开方向键：标定模式下必须发"松开"指令，否则设备会一直走。 */
    fun releaseDirection(left: Boolean) {
        if (!_fineTune.value) return
        repository.send(
            if (left) SliderCommand.MOVE_LEFT_RELEASE else SliderCommand.MOVE_RIGHT_RELEASE,
        )
    }

    /** 兜底：任何情况下都停止方向移动。 */
    fun releaseAllDirections() {
        repository.send(SliderCommand.MOVE_LEFT_RELEASE)
        repository.send(SliderCommand.MOVE_RIGHT_RELEASE)
    }

    // ---------------------------------------------------------------- 指令

    fun send(command: SliderCommand) = repository.send(command)

    fun sendAll(commands: List<SliderCommand>) = repository.sendAll(commands)

    fun startRun() {
        _runIntent.value = true
        repository.send(SliderCommand.START)
    }

    fun stopRun() {
        _runIntent.value = false
        releaseAllDirections()
        repository.send(SliderCommand.STOP)
    }

    // ---------------------------------------------------------------- 连接

    fun startScan() = repository.ble.startScan()

    fun stopScan() = repository.ble.stopScan()

    fun connect(address: String) = repository.ble.connect(address)

    fun disconnect() {
        _runIntent.value = false
        _fineTune.value = false
        _timerDraft.value = null
        repository.ble.disconnect()
    }

    fun refresh() = repository.requestFullSync()

    /** 兜底复位链路（扫描/连接卡死时用，不必杀进程）。 */
    fun resetLink() = repository.ble.forceReset()

    /** 当前是否已连上（用于禁止"已连接时扫描"）。 */
    fun isConnected() = connection.value.isUsable

    fun sendRawHex(hex: String): Boolean {
        val cleaned = hex.replace(" ", "").replace("\n", "").trim()
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return false
        val bytes = ByteArray(cleaned.length / 2) { index ->
            cleaned.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: return false
        }
        repository.sendRaw(bytes)
        return true
    }

    // ---------------------------------------------------------------- 权限 / 蓝牙状态

    fun hasPermissions() = repository.ble.hasPermissions()

    fun requiredPermissions() = repository.ble.requiredPermissions

    fun isBluetoothEnabled() = repository.ble.isBluetoothEnabled()

    fun isBluetoothSupported() = repository.ble.isBluetoothSupported()

    companion object {
        /** "关闭往返"时最多连按多少次 −，避免"自动"档位打出过长指令串。 */
        private const val MAX_REPEAT_STEPS = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SliderViewModel(container.repository) }
        }
    }
}
