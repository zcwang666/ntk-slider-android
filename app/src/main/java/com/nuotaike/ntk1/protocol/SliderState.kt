package com.nuotaike.ntk1.protocol

/**
 * 设备状态的唯一真相。
 *
 * 设计原则：**一切以设备上报为准**。App 的本地操作只做乐观提示，
 * 收到上报后立即以设备值覆盖，绝不出现"界面显示的和机器实际做的不一致"。
 */
data class SliderState(
    val firmware: Int? = null,
    val mode: SliderMode = SliderMode.VIDEO,
    val playing: Boolean = false,
    val directionForward: Boolean = true,
    val speedPercent: Double? = null,
    val positionMm: Int = 0,
    val travelMm: Int? = null,
    val shotsDone: Int = 0,
    val photoTarget: Int? = null,
    val photosRemaining: Int? = null,
    /** 原始往返次数：255 = 自动，0 = 关闭，其余为次数。 */
    val repeatRaw: Int = 0,
    /** 设备上报的延时设置（已还原为秒）。 */
    val delaySetting: Int? = null,
    /** 另一路延时上报（秒）。 */
    val delaySeconds: Int? = null,
    val stabilizationSeconds: Double? = null,
    val timerHours: Int = 0,
    val timerMinutes: Int = 0,
    val timerSeconds: Int = 0,
    /** 定时器时间是否已收到过上报。 */
    val timerKnown: Boolean = false,
    val timerOn: Boolean = false,
    val batteryVolts: Double? = null,
    val railModel: Int? = null,
    val keyLocked: Boolean = false,
    val systemOn: Boolean = true,
    val batteryIndicatorOn: Boolean = false,
    val sleeping: Boolean = false,
    val memoryPosition: Int = 0,
    val language: SliderLanguage = SliderLanguage.CHINESE,
    val setupLocked: Boolean = false,
    val updatedAt: Long = 0L,
) {
    /** 固件版本：设备只区分"3.0"及其以前。 */
    val firmwareLabel: String
        get() = when {
            firmware == null -> "—"
            firmware >= 3 -> "3.0"
            else -> "2.0"
        }

    /** 行程进度 0..1，行程未知时返回 0。 */
    val progress: Float
        get() {
            val total = travelMm ?: return 0f
            if (total <= 0) return 0f
            return (positionMm.toFloat() / total).coerceIn(0f, 1f)
        }

    val repeatIsAuto: Boolean get() = repeatRaw == 255
    val repeatIsOff: Boolean get() = repeatRaw == 0

    /** 往返次数的可读文本。 */
    val repeatLabel: String
        get() = when {
            repeatIsAuto -> "自动"
            repeatIsOff -> "关闭"
            else -> repeatRaw.toString()
        }

    /** 延时显示的优先取值。 */
    val delayDisplay: Int? get() = delaySetting ?: delaySeconds

    /** 速度百分比的显示文本。 */
    val speedLabel: String get() = speedPercent?.let { "%.1f".format(it) } ?: "--"

    /** 定时器/剩余时间，形如 00:12:30。 */
    val timerLabel: String
        get() = if (!timerKnown) "--:--:--" else "%02d:%02d:%02d".format(timerHours, timerMinutes, timerSeconds)

    /** 是否有任何一项遥测已到达。 */
    val hasTelemetry: Boolean get() = updatedAt > 0L

    fun reduce(update: TelemetryUpdate): SliderState = copy(
        firmware = update.firmwareVersion ?: firmware,
        mode = update.mode ?: mode,
        playing = update.playing ?: playing,
        directionForward = update.directionForward ?: directionForward,
        speedPercent = update.speedPercent ?: speedPercent,
        positionMm = update.positionMm ?: positionMm,
        travelMm = update.travelMm ?: travelMm,
        shotsDone = update.shotsDone ?: shotsDone,
        photoTarget = update.photoTarget ?: photoTarget,
        photosRemaining = update.photosRemaining ?: photosRemaining,
        repeatRaw = update.repeatRaw ?: repeatRaw,
        delaySetting = update.delaySetting ?: delaySetting,
        delaySeconds = update.delaySeconds ?: delaySeconds,
        stabilizationSeconds = update.stabilizationSeconds ?: stabilizationSeconds,
        timerHours = update.timerHours ?: timerHours,
        timerMinutes = update.remainingMinutes ?: timerMinutes,
        timerSeconds = update.remainingSeconds ?: timerSeconds,
        timerKnown = timerKnown || update.timerHours != null ||
            update.remainingMinutes != null || update.remainingSeconds != null,
        timerOn = update.timerOn ?: timerOn,
        batteryVolts = update.batteryVolts ?: batteryVolts,
        railModel = update.railModel ?: railModel,
        keyLocked = update.keyLocked ?: keyLocked,
        systemOn = update.systemOn ?: systemOn,
        batteryIndicatorOn = update.batteryIndicatorOn ?: batteryIndicatorOn,
        sleeping = update.sleeping ?: sleeping,
        memoryPosition = update.memoryPosition ?: memoryPosition,
        language = update.language ?: language,
        setupLocked = update.setupLocked ?: setupLocked,
        updatedAt = System.currentTimeMillis(),
    )

    companion object {
        val EMPTY = SliderState()
    }
}
