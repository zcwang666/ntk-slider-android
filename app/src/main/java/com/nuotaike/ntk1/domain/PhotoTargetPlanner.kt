package com.nuotaike.ntk1.domain

import com.nuotaike.ntk1.protocol.SliderCommand

/**
 * 照片目标张数的指令规划。
 *
 * 真机与官方界面确认的档位含义：
 * - A1 / A2 / A3 = **+1 / +10 / +100**
 * - B1 / B2 / B3 = **−1 / −10 / −100**
 *
 * 协议没有"直接设定张数"的指令，所以输入一个目标值时，
 * 把「当前值 → 目标值」的差值拆成这三档的组合再逐条下发。
 * 拆法是十进制逐位分解，命令数最少且不会来回过冲。
 */
object PhotoTargetPlanner {

    /** 单次下发命令数的上限，避免一个误输入打出一长串指令。 */
    const val MAX_COMMANDS = 80

    data class Step(
        val command: SliderCommand,
        val times: Int,
        val deltaEach: Int,
    )

    /**
     * 计算从 [from] 到 [to] 需要下发的指令序列（按执行顺序）。
     *
     * @return 指令列表；已相等或差值过大时返回空列表
     */
    fun plan(from: Int, to: Int): List<SliderCommand> {
        if (from == to) return emptyList()

        val diff = to - from
        val increase = diff > 0
        var remaining = kotlin.math.abs(diff)

        val commands = ArrayList<SliderCommand>(16)

        val hundreds = remaining / 100
        remaining %= 100
        val tens = remaining / 10
        val ones = remaining % 10

        // 大步长优先，减少指令条数
        repeat(hundreds) {
            commands += if (increase) SliderCommand.PHOTO_PRESET_A3 else SliderCommand.PHOTO_PRESET_B3
        }
        repeat(tens) {
            commands += if (increase) SliderCommand.PHOTO_PRESET_A2 else SliderCommand.PHOTO_PRESET_B2
        }
        repeat(ones) {
            commands += if (increase) SliderCommand.PHOTO_PRESET_A1 else SliderCommand.PHOTO_PRESET_B1
        }

        return if (commands.size > MAX_COMMANDS) emptyList() else commands
    }
}
