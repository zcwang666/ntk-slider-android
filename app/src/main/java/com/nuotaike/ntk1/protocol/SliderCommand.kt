package com.nuotaike.ntk1.protocol

/**
 * NTK-1 滑轨的全部下行指令（78 条）。
 *
 * 帧结构：`A5 5A 04 CMD_H CMD_L AA`（6 字节，固定长度，无数据段）
 * 逐字节复刻自官方小程序解包产物，未做任何改动，保证设备行为 100% 一致。
 */
enum class SliderCommand(
    val hi: Int,
    val lo: Int,
    val label: String,
) {
    // ---------- 会话 ----------
    HANDSHAKE(0xE7, 0xB6, "握手"),
    GO_HOME(0xE7, 0xB9, "返回主界面"),
    GO_SETUP(0xE6, 0xB9, "进入设置界面"),
    QUERY_ALL(0xA9, 0xB9, "查询全部状态"),

    // ---------- 运行 ----------
    START(0xFF, 0xB9, "启动"),
    STOP(0xB5, 0xB9, "停止"),
    SHUTTER(0xA8, 0xB6, "快门触发"),

    // ---------- 点动 ----------
    MOVE_LEFT(0xB4, 0xB8, "向左"),
    MOVE_RIGHT(0xB6, 0xBA, "向右"),
    MOVE_UP(0xB2, 0xB6, "上升"),
    MOVE_DOWN(0xB8, 0xBC, "下降"),

    // ---------- 模式 ----------
    CYCLE_MODE(0xB1, 0xB5, "模式轮换"),
    MODE_VIDEO(0xE8, 0xB6, "视频模式"),
    MODE_LAPSE(0xE9, 0xB6, "延时摄影"),
    MODE_ANIMATION(0xEA, 0xB6, "定格动画"),
    MODE_TRIGGER(0xEB, 0xB6, "外触延时"),

    // ---------- 参数步进 ----------
    DELAY_DEC(0xA3, 0xB6, "延时 -"),
    DELAY_INC(0xA2, 0xB6, "延时 +"),
    STABI_DEC(0x1C, 0xB6, "防抖 -"),
    STABI_INC(0x1B, 0xB6, "防抖 +"),
    REPEAT_DEC(0xC1, 0xB6, "往返 -"),
    REPEAT_INC(0xC0, 0xB6, "往返 +"),
    TIMER_TOGGLE(0xDD, 0xBA, "定时器开关"),

    // ---------- 定时器预置 ----------
    TIMER_PRESET_A1(0xE4, 0xB6, "定时器预置 A1"),
    TIMER_PRESET_A2(0xE2, 0xB6, "定时器预置 A2"),
    TIMER_PRESET_A3(0xE0, 0xB6, "定时器预置 A3"),
    TIMER_PRESET_B1(0xE5, 0xB6, "定时器预置 B1"),
    TIMER_PRESET_B2(0xE3, 0xB6, "定时器预置 B2"),
    TIMER_PRESET_B3(0xE1, 0xB6, "定时器预置 B3"),

    // ---------- 相片张数预置 ----------
    PHOTO_PRESET_A1(0xD1, 0xB6, "张数预置 A1"),
    PHOTO_PRESET_A2(0xD3, 0xB6, "张数预置 A2"),
    PHOTO_PRESET_A3(0xD5, 0xB6, "张数预置 A3"),
    PHOTO_PRESET_B1(0xD2, 0xB6, "张数预置 B1"),
    PHOTO_PRESET_B2(0xD4, 0xB6, "张数预置 B2"),
    PHOTO_PRESET_B3(0xD6, 0xB6, "张数预置 B3"),

    // ---------- 轨道型号预置 ----------
    MODEL_PRESET_A1(0x01, 0xB6, "型号预置 A1"),
    MODEL_PRESET_A2(0xA4, 0xB6, "型号预置 A2"),
    MODEL_PRESET_A3(0x03, 0xB6, "型号预置 A3"),
    MODEL_PRESET_B1(0x02, 0xB6, "型号预置 B1"),
    MODEL_PRESET_B2(0xA5, 0xB6, "型号预置 B2"),
    MODEL_PRESET_B3(0x04, 0xB6, "型号预置 B3"),

    // ---------- 行程预置 ----------
    LENGTH_PRESET_A1(0xAB, 0xB8, "行程预置 A1"),
    LENGTH_PRESET_A2(0x05, 0xB8, "行程预置 A2"),
    LENGTH_PRESET_A3(0x07, 0xB8, "行程预置 A3"),
    LENGTH_PRESET_B1(0xAC, 0xB8, "行程预置 B1"),
    LENGTH_PRESET_B2(0x06, 0xB8, "行程预置 B2"),
    LENGTH_PRESET_B3(0x08, 0xB8, "行程预置 B3"),

    // ---------- 行程档位（12 档，见 RailLength）----------
    LENGTH_1(0x11, 0xB8, "行程 1"),
    LENGTH_2(0x12, 0xB8, "行程 2"),
    LENGTH_3(0x13, 0xB8, "行程 3"),
    LENGTH_4(0x14, 0xB8, "行程 4"),
    LENGTH_5(0x15, 0xB8, "行程 5"),
    LENGTH_6(0x16, 0xB8, "行程 6"),
    LENGTH_7(0x17, 0xB8, "行程 7"),
    LENGTH_8(0x18, 0xB8, "行程 8"),
    LENGTH_9(0x19, 0xB8, "行程 9"),
    LENGTH_10(0x1A, 0xB8, "行程 10"),
    LENGTH_11(0x20, 0xB8, "行程 11"),
    LENGTH_12(0x21, 0xB8, "行程 12"),

    // ---------- 微调 / 打点模式 ----------
    //
    // 真机验证结论：方向点动必须先进"微调模式"（设备回报 F2=1）才生效。
    // 进模式后用持续的 CB/CC（左）与 C9/CA（右）按住走、松手停，
    // 而不是普通模式下的 B4B8 / B6BAAA。
    FINE_TUNE_ENTER(0xD8, 0xB6, "进入微调模式"),
    FINE_TUNE_TAP(0xD9, 0xB6, "微调短按"),
    FINE_TUNE_EXIT(0xDC, 0xBA, "退出微调模式"),
    MOVE_LEFT_HOLD(0xCB, 0xB6, "按住左移"),
    MOVE_LEFT_RELEASE(0xCC, 0xB6, "松开左移"),
    MOVE_RIGHT_HOLD(0xC9, 0xB6, "按住右移"),
    MOVE_RIGHT_RELEASE(0xCA, 0xB6, "松开右移"),

    // ---------- 系统 ----------
    PHOTO_CLEAR(0xAA, 0xB6, "相片清零"),
    MEMORY_POSITION_ON(0x25, 0xB8, "位置记忆"),
    PAIRING_MODE(0x23, 0xB8, "进入配对"),
    CLEAR_PAIRING(0x24, 0xB8, "清除配对"),
    LANGUAGE_TOGGLE(0xEC, 0xB8, "中英文切换"),
    BATTERY_INDICATOR_OFF(0xA7, 0xB6, "电量指示 关"),
    BATTERY_INDICATOR_ON(0xC5, 0xB6, "电量指示 开"),
    SYSTEM_POWER_OFF(0xA6, 0xB6, "系统电源 关"),
    SYSTEM_POWER_ON(0xC6, 0xB6, "系统电源 开"),
    KEY_LOCK(0xB7, 0xBA, "键锁"),
    SETUP_CONFIRM(0x22, 0xBA, "设置确认"),
    POWER_OFF(0xD0, 0xB6, "关机"),
    ;

    /** 完整 6 字节下行帧。 */
    val frame: ByteArray
        get() = FrameCodec.encode(this)

    /** 命令字（2 字节）的十六进制表示，用于日志与诊断。 */
    val opcode: String
        get() = "%02X%02X".format(hi, lo)

    companion object {
        /** 按命令字查找指令，用于日志反查。 */
        fun byOpcode(hi: Int, lo: Int): SliderCommand? =
            entries.firstOrNull { it.hi == hi && it.lo == lo }
    }
}

/**
 * 12 档固定行程（毫米）。
 *
 * 注意：官方设置页按"轨道型号"把档位分成两组显示，但 12 条指令本身固定不变
 * —— 档位与毫米的对应关系由固件决定，App 只做展示。
 */
enum class RailLength(val index: Int, val millimeters: Int, val command: SliderCommand) {
    L120(1, 120, SliderCommand.LENGTH_1),
    L220(2, 220, SliderCommand.LENGTH_2),
    L320(3, 320, SliderCommand.LENGTH_3),
    L350(4, 350, SliderCommand.LENGTH_4),
    L550(5, 550, SliderCommand.LENGTH_5),
    L750(6, 750, SliderCommand.LENGTH_6),
    L950(7, 950, SliderCommand.LENGTH_7),
    L1250(8, 1250, SliderCommand.LENGTH_8),
    L370(9, 370, SliderCommand.LENGTH_9),
    L570(10, 570, SliderCommand.LENGTH_10),
    L770(11, 770, SliderCommand.LENGTH_11),
    L970(12, 970, SliderCommand.LENGTH_12),
    ;

    companion object {
        fun byMillimeters(mm: Int): RailLength? = entries.firstOrNull { it.millimeters == mm }
    }
}
