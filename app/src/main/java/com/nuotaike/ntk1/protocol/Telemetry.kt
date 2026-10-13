package com.nuotaike.ntk1.protocol

/** 设备工作模式。 */
enum class SliderMode(
    val code: Int,
    val zh: String,
    val en: String,
    /** 切换到该模式的绝对指令。 */
    val command: SliderCommand,
) {
    VIDEO(1, "视频", "VIDEO", SliderCommand.MODE_VIDEO),
    TIME_LAPSE(2, "延时", "LAPSE", SliderCommand.MODE_LAPSE),
    ANIMATION(3, "定格", "ANIMA", SliderCommand.MODE_ANIMATION),
    EXTERNAL_TRIGGER(4, "外触", "TRIG", SliderCommand.MODE_TRIGGER),
    ;

    companion object {
        fun fromCode(code: Int): SliderMode? = entries.firstOrNull { it.code == code }
    }
}

/** 设备语言设置。 */
enum class SliderLanguage(val code: Int) {
    CHINESE(1),
    ENGLISH(2),
    ;

    companion object {
        fun fromCode(code: Int): SliderLanguage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 一次上行解析的结果；未涉及的字段为 `null`，表示"本次上报没带这个信息"。
 *
 * 与官方实现不同，这里对每个值做了**范围校验**：超出物理量程的值判定为噪声并丢弃，
 * 而不是把脏数据写进界面（原版会把任意值直接 setData，导致界面显示不可能的数字）。
 */
data class TelemetryUpdate(
    val delaySeconds: Int? = null,
    val positionMm: Int? = null,
    val shotsDone: Int? = null,
    val timerHours: Int? = null,
    val batteryVolts: Double? = null,
    val remainingMinutes: Int? = null,
    val remainingSeconds: Int? = null,
    val travelMm: Int? = null,
    val railModel: Int? = null,
    val setupActive: Boolean? = null,
    val photoTarget: Int? = null,
    val delaySetting: Int? = null,
    val photosRemaining: Int? = null,
    val repeatRaw: Int? = null,
    val mode: SliderMode? = null,
    /** 速度百分比；设备上报的是 ×100 的整数，这里保留一位小数精度。 */
    val speedPercent: Double? = null,
    val stabilizationSeconds: Double? = null,
    val firmwareVersion: Int? = null,
    val directionForward: Boolean? = null,
    val keyLocked: Boolean? = null,
    val systemOn: Boolean? = null,
    val batteryIndicatorOn: Boolean? = null,
    val sleeping: Boolean? = null,
    val playing: Boolean? = null,
    val setupLocked: Boolean? = null,
    val timerOn: Boolean? = null,
    val requestOpenSetup: Boolean = false,
    val language: SliderLanguage? = null,
    val firstRun: Boolean? = null,
    val memoryPosition: Int? = null,
)

/**
 * 上行状态解析器。
 *
 * 上报命令字与含义全部还原自官方小程序的通知回调逻辑，见配套的 BLE 协议逆向文档。
 */
object TelemetryParser {

    private const val OP_DELAY = 0xC0
    private const val OP_POSITION = 0xC2
    private const val OP_SHOTS_DONE = 0xC3
    private const val OP_TIMER_HOURS = 0xC4
    private const val OP_BATTERY = 0xC5
    private const val OP_REMAINING = 0xC6
    private const val OP_TRAVEL = 0xC7
    private const val OP_RAIL_MODEL = 0xC8
    private const val OP_SETUP_ACTIVE = 0xC9
    private const val OP_PHOTO_TARGET = 0xCA
    private const val OP_DELAY_SETTING = 0xCB
    private const val OP_PHOTOS_REMAINING = 0xCD
    private const val OP_REPEAT = 0xCF
    private const val OP_MODE = 0xD0
    private const val OP_SPEED = 0xD1
    private const val OP_STABILIZATION = 0xD2
    private const val OP_FIRMWARE = 0xD3
    private const val OP_DIR_FORWARD = 0xE5
    private const val OP_DIR_BACKWARD = 0xE6
    private const val OP_KEY_LOCK = 0xE9
    private const val OP_SYSTEM_POWER = 0xEA
    private const val OP_BATTERY_INDICATOR = 0xEB
    private const val OP_SLEEP = 0xEC
    private const val OP_PLAYING = 0xF1
    private const val OP_SETUP_LOCKED = 0xF2
    private const val OP_TIMER_ON = 0xF4
    private const val OP_OPEN_SETUP = 0xF5
    private const val OP_LANGUAGE = 0xF7
    private const val OP_FIRST_RUN = 0xF8
    private const val OP_MEMORY_POSITION = 0xF9

    /** 物理量程（毫米）。超过此值判定为解析噪声。 */
    private const val MAX_MILLIMETERS = 100_000
    private const val MAX_SECONDS = 24 * 3600

    fun parse(frame: ByteArray): TelemetryUpdate? {
        if (frame.size < 6) return null
        if (FrameCodec.u(frame[0]) != FrameCodec.HEADER_0) return null
        if (FrameCodec.u(frame[1]) != FrameCodec.HEADER_1) return null

        val op = FrameCodec.telemetryOpcode(frame)
        val v = FrameCodec.telemetryValue(frame)

        return when (op) {
            OP_DELAY -> v.takeIf { it in 0..MAX_SECONDS }?.let { TelemetryUpdate(delaySeconds = it) }

            OP_POSITION -> v.takeIf { it in 0..MAX_MILLIMETERS }
                ?.let { TelemetryUpdate(positionMm = it) }

            OP_SHOTS_DONE -> v.takeIf { it in 0..99_999 }
                ?.let { TelemetryUpdate(shotsDone = it) }

            OP_TIMER_HOURS -> v.takeIf { it in 0..99 }
                ?.let { TelemetryUpdate(timerHours = it) }

            // 电量以"厘伏"上报：1240 → 12.40 V
            OP_BATTERY -> (v / 100.0).takeIf { it in 2.0..30.0 }
                ?.let { TelemetryUpdate(batteryVolts = it) }

            // 剩余时间打包为 分*100 + 秒
            OP_REMAINING -> v.takeIf { it in 0..(99 * 100 + 59) }?.let {
                TelemetryUpdate(remainingMinutes = it / 100, remainingSeconds = it % 100)
            }

            OP_TRAVEL -> v.takeIf { it in 1..MAX_MILLIMETERS }
                ?.let { TelemetryUpdate(travelMm = it) }

            OP_RAIL_MODEL -> v.takeIf { it in 0..9_999 }
                ?.let { TelemetryUpdate(railModel = it) }

            OP_SETUP_ACTIVE -> TelemetryUpdate(setupActive = v != 0)

            OP_PHOTO_TARGET -> v.takeIf { it in 0..99_999 }
                ?.let { TelemetryUpdate(photoTarget = it) }

            // 设备上报的是"延时 - 1"，原版据此 +1 还原
            OP_DELAY_SETTING -> (v + 1).takeIf { it in 0..MAX_SECONDS }
                ?.let { TelemetryUpdate(delaySetting = it) }

            OP_PHOTOS_REMAINING -> v.takeIf { it in 0..99_999 }
                ?.let { TelemetryUpdate(photosRemaining = it) }

            // 255 = 自动，0 = 关闭，其余为次数
            OP_REPEAT -> v.takeIf { it in 0..255 }?.let { TelemetryUpdate(repeatRaw = it) }

            OP_MODE -> SliderMode.fromCode(v)?.let { TelemetryUpdate(mode = it) }

            // 速度以百分比 * 100 上报：1549 → 15.49%
            OP_SPEED -> (v / 100.0).takeIf { it in 0.0..100.0 }
                ?.let { TelemetryUpdate(speedPercent = it) }

            // 防抖以 0.5 秒为步长：6 → 3.0 s
            OP_STABILIZATION -> (v / 2.0).takeIf { it in 0.0..600.0 }
                ?.let { TelemetryUpdate(stabilizationSeconds = it) }

            OP_FIRMWARE -> v.takeIf { it in 0..99 }?.let { TelemetryUpdate(firmwareVersion = it) }

            OP_DIR_FORWARD -> TelemetryUpdate(directionForward = v != 0)
            OP_DIR_BACKWARD -> TelemetryUpdate(directionForward = v == 0)

            OP_KEY_LOCK -> TelemetryUpdate(keyLocked = v != 0)
            OP_SYSTEM_POWER -> TelemetryUpdate(systemOn = v != 0)
            OP_BATTERY_INDICATOR -> TelemetryUpdate(batteryIndicatorOn = v != 0)
            OP_SLEEP -> TelemetryUpdate(sleeping = v != 0)
            OP_PLAYING -> TelemetryUpdate(playing = v != 0)
            OP_SETUP_LOCKED -> TelemetryUpdate(setupLocked = v != 0)
            OP_TIMER_ON -> TelemetryUpdate(timerOn = v != 0)

            // 设备主动要求 App 打开设置页
            OP_OPEN_SETUP -> TelemetryUpdate(requestOpenSetup = v != 0)

            OP_LANGUAGE -> SliderLanguage.fromCode(v)?.let { TelemetryUpdate(language = it) }
            OP_FIRST_RUN -> TelemetryUpdate(firstRun = v != 0)
            OP_MEMORY_POSITION -> v.takeIf { it in 0..2 }?.let { TelemetryUpdate(memoryPosition = it) }

            else -> null
        }
    }
}
