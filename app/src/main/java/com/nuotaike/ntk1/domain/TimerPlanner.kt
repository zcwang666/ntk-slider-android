package com.nuotaike.ntk1.domain

import com.nuotaike.ntk1.protocol.SliderCommand

/**
 * 定时器时长的指令规划。
 *
 * 档位含义由官方函数名确认（`timer01/timer11`、`timer02/timer12`、`timer03/timer13`：
 * 第一位 0=减 / 1=加，第二位 1=秒 / 2=分 / 3=时）：
 *
 * | | 秒 | 分 | 时 |
 * |---|---|---|---|
 * | ＋ | A1 | A2 | A3 |
 * | − | B1 | B2 | B3 |
 *
 * 协议没有"直接设定时长"的指令，所以输入一个时分秒后，
 * 按各单位的差值补齐对应次数的 ± 指令。
 */
object TimerPlanner {

    /** 单次下发命令数上限，避免一个误输入打出一长串指令。 */
    const val MAX_COMMANDS = 200

    const val MAX_HOURS = 99
    const val MAX_MINUTES = 59
    const val MAX_SECONDS = 59

    fun plan(
        fromHours: Int,
        toHours: Int,
        fromMinutes: Int,
        toMinutes: Int,
        fromSeconds: Int,
        toSeconds: Int,
    ): List<SliderCommand> {
        val commands = ArrayList<SliderCommand>(32)

        fun step(delta: Int, plus: SliderCommand, minus: SliderCommand) {
            repeat(kotlin.math.abs(delta)) {
                commands += if (delta > 0) plus else minus
            }
        }

        step(toHours - fromHours, SliderCommand.TIMER_PRESET_A3, SliderCommand.TIMER_PRESET_B3)
        step(toMinutes - fromMinutes, SliderCommand.TIMER_PRESET_A2, SliderCommand.TIMER_PRESET_B2)
        step(toSeconds - fromSeconds, SliderCommand.TIMER_PRESET_A1, SliderCommand.TIMER_PRESET_B1)

        return if (commands.size > MAX_COMMANDS) emptyList() else commands
    }
}
