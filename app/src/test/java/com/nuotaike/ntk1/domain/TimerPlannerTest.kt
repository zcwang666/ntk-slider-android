package com.nuotaike.ntk1.domain

import com.nuotaike.ntk1.protocol.SliderCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定时器时长规划测试。
 *
 * 档位含义由官方函数名确认（timer01/timer11 等）：
 * ＋ 秒/分/时 = A1/A2/A3，− 秒/分/时 = B1/B2/B3。
 */
class TimerPlannerTest {

    private fun plan(fromH: Int = 0, toH: Int = 0, fromM: Int = 0, toM: Int = 0, fromS: Int = 0, toS: Int = 0) =
        TimerPlanner.plan(fromH, toH, fromM, toM, fromS, toS)

    @Test
    fun `完全相同时不产生指令`() {
        assertTrue(plan(fromH = 1, toH = 1, fromM = 2, toM = 2, fromS = 3, toS = 3).isEmpty())
    }

    @Test
    fun `秒用 A1 B1`() {
        assertEquals(listOf(SliderCommand.TIMER_PRESET_A1), plan(fromS = 10, toS = 11))
        assertEquals(listOf(SliderCommand.TIMER_PRESET_B1), plan(fromS = 10, toS = 9))
    }

    @Test
    fun `分用 A2 B2`() {
        assertEquals(listOf(SliderCommand.TIMER_PRESET_A2), plan(fromM = 10, toM = 11))
        assertEquals(listOf(SliderCommand.TIMER_PRESET_B2), plan(fromM = 10, toM = 9))
    }

    @Test
    fun `时用 A3 B3`() {
        assertEquals(listOf(SliderCommand.TIMER_PRESET_A3), plan(fromH = 1, toH = 2))
        assertEquals(listOf(SliderCommand.TIMER_PRESET_B3), plan(fromH = 2, toH = 1))
    }

    @Test
    fun `三个单位可以同时调整`() {
        val commands = plan(fromH = 0, toH = 2, fromM = 0, toM = 1, fromS = 19, toS = 15)
        assertEquals(2, commands.count { it == SliderCommand.TIMER_PRESET_A3 })
        assertEquals(1, commands.count { it == SliderCommand.TIMER_PRESET_A2 })
        assertEquals(4, commands.count { it == SliderCommand.TIMER_PRESET_B1 })
        assertEquals(7, commands.size)
    }

    @Test
    fun `时先于分秒下发`() {
        val commands = plan(fromH = 0, toH = 1, fromM = 0, toM = 1, fromS = 0, toS = 1)
        assertEquals(
            listOf(
                SliderCommand.TIMER_PRESET_A3,
                SliderCommand.TIMER_PRESET_A2,
                SliderCommand.TIMER_PRESET_A1,
            ),
            commands,
        )
    }

    @Test
    fun `不会混入另一方向的指令`() {
        val commands = plan(fromH = 0, toH = 3)
        assertTrue(commands.all { it == SliderCommand.TIMER_PRESET_A3 })
    }
}
