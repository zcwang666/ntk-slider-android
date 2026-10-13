package com.nuotaike.ntk1.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上行状态解析的回归用例。
 *
 * 构造方式与真实链路一致：`A5 5A LEN CMD DATA_H DATA_L X AA`，
 * 数值为 16 位大端——与官方小程序 `e(substring(...))` 的还原方式一致。
 */
class TelemetryParserTest {

    private fun rx(opcode: Int, value: Int): ByteArray = byteArrayOf(
        0xA5.toByte(),
        0x5A,
        0x06,
        opcode.toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
        0x00,
        0xAA.toByte(),
    )

    @Test
    fun `电量以厘伏上报并换算为伏特`() {
        val update = TelemetryParser.parse(rx(0xC5, 1240))
        assertEquals(12.40, update!!.batteryVolts!!, 1e-9)
    }

    @Test
    fun `超出量程的电量被判定为噪声`() {
        assertNull(TelemetryParser.parse(rx(0xC5, 100))?.batteryVolts)
        assertNull(TelemetryParser.parse(rx(0xC5, 5000))?.batteryVolts)
    }

    @Test
    fun `速度以百分比乘一百上报并保留小数`() {
        assertEquals(60.0, TelemetryParser.parse(rx(0xD1, 6000))!!.speedPercent!!, 1e-9)
        assertEquals(15.49, TelemetryParser.parse(rx(0xD1, 1549))!!.speedPercent!!, 1e-9)
        assertEquals(0.0, TelemetryParser.parse(rx(0xD1, 0))!!.speedPercent!!, 1e-9)
    }

    @Test
    fun `超范围的速度被丢弃`() {
        assertNull(TelemetryParser.parse(rx(0xD1, 20000))?.speedPercent)
    }

    @Test
    fun `防抖以半秒为步长`() {
        assertEquals(3.0, TelemetryParser.parse(rx(0xD2, 6))!!.stabilizationSeconds!!, 1e-9)
    }

    @Test
    fun `剩余时间按分乘一百加秒解包`() {
        val update = TelemetryParser.parse(rx(0xC6, 1230))!!
        assertEquals(12, update.remainingMinutes)
        assertEquals(30, update.remainingSeconds)
    }

    @Test
    fun `延时设置有加一还原`() {
        assertEquals(5, TelemetryParser.parse(rx(0xCB, 4))!!.delaySetting)
    }

    @Test
    fun `延时上报原样返回`() {
        assertEquals(30, TelemetryParser.parse(rx(0xC0, 30))!!.delaySeconds)
    }

    @Test
    fun `位置与行程按毫米解析`() {
        assertEquals(480, TelemetryParser.parse(rx(0xC2, 480))!!.positionMm)
        assertEquals(1250, TelemetryParser.parse(rx(0xC7, 1250))!!.travelMm)
    }

    @Test
    fun `行程为零被丢弃`() {
        assertNull(TelemetryParser.parse(rx(0xC7, 0))?.travelMm)
    }

    @Test
    fun `模式映射正确`() {
        assertEquals(SliderMode.VIDEO, TelemetryParser.parse(rx(0xD0, 1))!!.mode)
        assertEquals(SliderMode.TIME_LAPSE, TelemetryParser.parse(rx(0xD0, 2))!!.mode)
        assertEquals(SliderMode.ANIMATION, TelemetryParser.parse(rx(0xD0, 3))!!.mode)
        assertEquals(SliderMode.EXTERNAL_TRIGGER, TelemetryParser.parse(rx(0xD0, 4))!!.mode)
        assertNull(TelemetryParser.parse(rx(0xD0, 9))?.mode)
    }

    @Test
    fun `往返次数的三种语义`() {
        val auto = SliderState.EMPTY.reduce(TelemetryParser.parse(rx(0xCF, 255))!!)
        assertTrue(auto.repeatIsAuto)
        assertEquals("自动", auto.repeatLabel)

        val off = SliderState.EMPTY.reduce(TelemetryParser.parse(rx(0xCF, 0))!!)
        assertTrue(off.repeatIsOff)
        assertEquals("关闭", off.repeatLabel)

        val times = SliderState.EMPTY.reduce(TelemetryParser.parse(rx(0xCF, 12))!!)
        assertEquals("12", times.repeatLabel)
    }

    @Test
    fun `方向上报的两个命令字`() {
        assertTrue(TelemetryParser.parse(rx(0xE5, 1))!!.directionForward!!)
        assertFalse(TelemetryParser.parse(rx(0xE6, 1))!!.directionForward!!)
    }

    @Test
    fun `布尔状态按非零判定`() {
        assertTrue(TelemetryParser.parse(rx(0xF1, 1))!!.playing!!)
        assertFalse(TelemetryParser.parse(rx(0xF1, 0))!!.playing!!)
        assertTrue(TelemetryParser.parse(rx(0xE9, 1))!!.keyLocked!!)
        assertTrue(TelemetryParser.parse(rx(0xEC, 1))!!.sleeping!!)
    }

    @Test
    fun `语言与首次运行标志`() {
        assertEquals(SliderLanguage.CHINESE, TelemetryParser.parse(rx(0xF7, 1))!!.language)
        assertEquals(SliderLanguage.ENGLISH, TelemetryParser.parse(rx(0xF7, 2))!!.language)
        assertTrue(TelemetryParser.parse(rx(0xF8, 1))!!.firstRun!!)
    }

    @Test
    fun `设备可主动要求打开设置页`() {
        assertTrue(TelemetryParser.parse(rx(0xF5, 1))!!.requestOpenSetup)
    }

    @Test
    fun `固件版本上报`() {
        assertEquals(3, TelemetryParser.parse(rx(0xD3, 3))!!.firmwareVersion)
    }

    @Test
    fun `未知命令字返回空`() {
        assertNull(TelemetryParser.parse(rx(0x77, 1)))
    }

    @Test
    fun `帧头错误或长度不足时返回空`() {
        assertNull(TelemetryParser.parse(byteArrayOf(0x00, 0x5A, 0x06, 0xC5.toByte(), 0x04, 0xD2.toByte(), 0x00, 0xAA.toByte())))
        assertNull(TelemetryParser.parse(byteArrayOf(0xA5.toByte(), 0x5A, 0x06, 0xC5.toByte())))
    }
}

class SliderStateTest {

    @Test
    fun `初始状态没有任何遥测`() {
        assertFalse(SliderState.EMPTY.hasTelemetry)
        assertEquals("--:--:--", SliderState.EMPTY.timerLabel)
        assertEquals(0f, SliderState.EMPTY.progress, 0f)
        assertEquals("—", SliderState.EMPTY.firmwareLabel)
    }

    @Test
    fun `多次上报按字段合并且互不覆盖`() {
        var state = SliderState.EMPTY
        state = state.reduce(TelemetryUpdate(positionMm = 300))
        state = state.reduce(TelemetryUpdate(travelMm = 1250))
        state = state.reduce(TelemetryUpdate(batteryVolts = 12.4))

        assertEquals(300, state.positionMm)
        assertEquals(1250, state.travelMm)
        assertEquals(12.4, state.batteryVolts!!, 1e-9)
        assertTrue(state.hasTelemetry)
    }

    @Test
    fun `行程进度被限制在零到一之间`() {
        val state = SliderState.EMPTY
            .reduce(TelemetryUpdate(travelMm = 1000))
            .reduce(TelemetryUpdate(positionMm = 2500))
        assertEquals(1f, state.progress, 1e-6f)
    }

    @Test
    fun `固件版本标签与原版一致`() {
        assertEquals("3.0", SliderState.EMPTY.reduce(TelemetryUpdate(firmwareVersion = 3)).firmwareLabel)
        assertEquals("3.0", SliderState.EMPTY.reduce(TelemetryUpdate(firmwareVersion = 4)).firmwareLabel)
        assertEquals("2.0", SliderState.EMPTY.reduce(TelemetryUpdate(firmwareVersion = 2)).firmwareLabel)
    }

    @Test
    fun `定时器时间累计后正确格式化`() {
        val state = SliderState.EMPTY
            .reduce(TelemetryUpdate(timerHours = 2))
            .reduce(TelemetryUpdate(remainingMinutes = 12, remainingSeconds = 30))
        assertEquals("02:12:30", state.timerLabel)
    }

    @Test
    fun `延时显示优先使用设置值`() {
        val state = SliderState.EMPTY
            .reduce(TelemetryUpdate(delaySeconds = 10))
            .reduce(TelemetryUpdate(delaySetting = 25))
        assertEquals(25, state.delayDisplay)
    }

    @Test
    fun `十二档行程与官方毫米值一致`() {
        val expected = listOf(120, 220, 320, 350, 550, 750, 950, 1250, 370, 570, 770, 970)
        assertEquals(expected, RailLength.entries.map { it.millimeters })
        assertEquals(RailLength.L1250, RailLength.byMillimeters(1250))
        assertNull(RailLength.byMillimeters(999))
    }
}
