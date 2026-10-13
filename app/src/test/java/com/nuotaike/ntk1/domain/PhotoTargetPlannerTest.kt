package com.nuotaike.ntk1.domain

import com.nuotaike.ntk1.protocol.SliderCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 照片目标张数的指令规划测试。
 *
 * 档位含义（官方界面确认）：
 * A1/A2/A3 = +1/+10/+100，B1/B2/B3 = −1/−10/−100。
 */
class PhotoTargetPlannerTest {

    private fun count(list: List<SliderCommand>, c: SliderCommand) = list.count { it == c }

    @Test
    fun `目标与当前相同时不产生指令`() {
        assertTrue(PhotoTargetPlanner.plan(from = 100, to = 100).isEmpty())
    }

    @Test
    fun `加一使用 A1`() {
        val plan = PhotoTargetPlanner.plan(from = 10, to = 11)
        assertEquals(listOf(SliderCommand.PHOTO_PRESET_A1), plan)
    }

    @Test
    fun `减一使用 B1`() {
        val plan = PhotoTargetPlanner.plan(from = 10, to = 9)
        assertEquals(listOf(SliderCommand.PHOTO_PRESET_B1), plan)
    }

    @Test
    fun `十位用 A2 百位用 A3`() {
        val plan = PhotoTargetPlanner.plan(from = 0, to = 120)
        assertEquals(1, count(plan, SliderCommand.PHOTO_PRESET_A3))
        assertEquals(2, count(plan, SliderCommand.PHOTO_PRESET_A2))
        assertEquals(0, count(plan, SliderCommand.PHOTO_PRESET_A1))
    }

    @Test
    fun `减法同理使用 B 组`() {
        val plan = PhotoTargetPlanner.plan(from = 300, to = 189)
        assertEquals(1, count(plan, SliderCommand.PHOTO_PRESET_B3))
        assertEquals(1, count(plan, SliderCommand.PHOTO_PRESET_B2))
        assertEquals(1, count(plan, SliderCommand.PHOTO_PRESET_B1))
    }

    @Test
    fun `逐位分解后指令数等于各数字位之和`() {
        // 99 = 9 个十 + 9 个一
        val plan = PhotoTargetPlanner.plan(from = 0, to = 99)
        assertEquals(18, plan.size)
    }

    @Test
    fun `只朝一个方向走不会来回过冲`() {
        val plan = PhotoTargetPlanner.plan(from = 50, to = 200)
        assertTrue(plan.all { it == SliderCommand.PHOTO_PRESET_A1 ||
            it == SliderCommand.PHOTO_PRESET_A2 || it == SliderCommand.PHOTO_PRESET_A3 })
    }

    @Test
    fun `差值过大时拒绝下发以免刷屏`() {
        assertTrue(PhotoTargetPlanner.plan(from = 0, to = 9999).isEmpty())
    }
}
