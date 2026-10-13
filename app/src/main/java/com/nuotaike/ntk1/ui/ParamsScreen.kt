package com.nuotaike.ntk1.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuotaike.ntk1.ble.ConnectionState
import com.nuotaike.ntk1.protocol.RailLength
import com.nuotaike.ntk1.protocol.SliderCommand
import com.nuotaike.ntk1.protocol.SliderState
import com.nuotaike.ntk1.ui.components.ActionTile
import com.nuotaike.ntk1.ui.components.BackHeader
import com.nuotaike.ntk1.ui.components.HoldRepeatButton
import com.nuotaike.ntk1.ui.components.InfoRow
import com.nuotaike.ntk1.ui.components.SectionCard
import com.nuotaike.ntk1.ui.theme.DangerRed
import com.nuotaike.ntk1.ui.theme.LiveCyan
import com.nuotaike.ntk1.ui.theme.SuccessGreen
import com.nuotaike.ntk1.ui.theme.WarningAmber

/** 参数区的二级页面。 */
private enum class ParamsPage {
    MENU,
    MOTION,
    TRAVEL,
    MAINTENANCE,
}

/**
 * 参数页：一级为分类菜单，二级为具体设置项。
 *
 * 分类与官方小程序「设置」页里的参数项一一对应，只是重新分组并补上了说明。
 */
@Composable
fun ParamsScreen(
    state: SliderState,
    connection: ConnectionState,
    onCommand: (SliderCommand) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageOrdinal by rememberSaveable { mutableIntStateOf(ParamsPage.MENU.ordinal) }
    val page = ParamsPage.entries[pageOrdinal]
    val enabled = connection.isUsable

    // 系统返回键：先回到一级菜单；已在一级菜单时交回上层（回到首页）
    BackHandler(enabled = page != ParamsPage.MENU) {
        pageOrdinal = ParamsPage.MENU.ordinal
    }

    when (page) {
        ParamsPage.MENU -> ParamsMenu(
            state = state,
            modifier = modifier,
            onOpen = { pageOrdinal = it.ordinal },
            onBack = onBack,
        )

        ParamsPage.MOTION -> DetailScaffold(
            title = "运动参数",
            subtitle = "MOTION",
            modifier = modifier,
            onBack = { pageOrdinal = ParamsPage.MENU.ordinal },
        ) {
            MotionSection(state = state, enabled = enabled, onCommand = onCommand)
        }

        ParamsPage.TRAVEL -> DetailScaffold(
            title = "行程与型号",
            subtitle = "TRAVEL / MODEL",
            modifier = modifier,
            onBack = { pageOrdinal = ParamsPage.MENU.ordinal },
        ) {
            TravelSection(state = state, enabled = enabled, onCommand = onCommand)
        }

        ParamsPage.MAINTENANCE -> DetailScaffold(
            title = "设备维护",
            subtitle = "SYSTEM",
            modifier = modifier,
            onBack = { pageOrdinal = ParamsPage.MENU.ordinal },
        ) {
            MaintenanceSection(state = state, enabled = enabled, onCommand = onCommand)
        }
    }
}

// ---------------------------------------------------------------- 一级菜单

@Composable
private fun ParamsMenu(
    state: SliderState,
    modifier: Modifier,
    onOpen: (ParamsPage) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(
            title = "设置",
            subtitle = "SETTINGS",
            onBack = onBack,
        )

        SectionCard(title = "参数设置", subtitle = "PARAMETERS") {
            Text(
                text = "按分类进入二级页面。所有设置项都直接对应滑轨控制器的参数，" +
                    "设备上报值会实时回显在摘要里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MenuRow(
            title = "运动参数",
            subtitle = "MOTION",
            summary = "延时 ${state.delayDisplay ?: "--"}s · 防抖 " +
                (state.stabilizationSeconds?.let { "%.1f".format(it) } ?: "--") +
                "s · 往返 ${state.repeatLabel}",
            onClick = { onOpen(ParamsPage.MOTION) },
        )

        MenuRow(
            title = "行程与型号",
            subtitle = "TRAVEL / MODEL",
            summary = "行程 ${state.travelMm?.let { "$it mm" } ?: "未知"} · 型号标定 " +
                (state.railModel?.toString() ?: "--"),
            onClick = { onOpen(ParamsPage.TRAVEL) },
        )

        MenuRow(
            title = "设备维护",
            subtitle = "SYSTEM",
            summary = "配对 · 位置记忆 · 系统电源 · 语言",
            onClick = { onOpen(ParamsPage.MAINTENANCE) },
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "当前模式：${state.mode.zh} · ${state.mode.en}　|　固件 ${state.firmwareLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    summary: String,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClick() }) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 二级页面外壳

@Composable
private fun DetailScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(title = title, subtitle = subtitle, onBack = onBack)

        content()

        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- 二级页面内容

@Composable
private fun MotionSection(
    state: SliderState,
    enabled: Boolean,
    onCommand: (SliderCommand) -> Unit,
) {
    SectionCard(title = "运动参数", subtitle = "MOTION") {
        Text(
            text = "延时、防抖、往返次数只能按步长增减（协议不提供「设定任意值」的指令）。" +
                "单击步进一次，长按可连续调整。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        StepperRow(
            label = "延时",
            englishLabel = "DELAY",
            value = state.delayDisplay?.toString() ?: "--",
            unit = "秒",
            enabled = enabled,
            onDec = { onCommand(SliderCommand.DELAY_DEC) },
            onInc = { onCommand(SliderCommand.DELAY_INC) },
        )
        Spacer(Modifier.height(10.dp))
        StepperRow(
            label = "防抖",
            englishLabel = "STABI",
            value = state.stabilizationSeconds?.let { "%.1f".format(it) } ?: "--",
            unit = "秒",
            enabled = enabled,
            onDec = { onCommand(SliderCommand.STABI_DEC) },
            onInc = { onCommand(SliderCommand.STABI_INC) },
        )
        Spacer(Modifier.height(10.dp))
        StepperRow(
            label = "往返",
            englishLabel = "REPEAT",
            value = state.repeatLabel,
            unit = "次",
            enabled = enabled,
            onDec = { onCommand(SliderCommand.REPEAT_DEC) },
            onInc = { onCommand(SliderCommand.REPEAT_INC) },
        )
    }

    SectionCard(title = "速度", subtitle = "SPEED") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.speedPercent?.toString() ?: "--",
                style = MaterialTheme.typography.displayMedium,
                color = WarningAmber,
            )
            Text(
                text = " %",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "设备上报值",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "⚠ 速度目前只能读取，不能远程调节。官方小程序的 78 条指令里没有任何一条" +
                "是设置速度的，速度由控制器本体决定。要做成可拖动滑块，需要固件新增一条" +
                "「设置速度」指令——这属于固件改动，App 侧接上只需很短时间。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TravelSection(
    state: SliderState,
    enabled: Boolean,
    onCommand: (SliderCommand) -> Unit,
) {
    SectionCard(title = "行程档位", subtitle = "LENGTH") {
        Text(
            text = "共 12 档固定行程；当前读数 " +
                (state.travelMm?.let { "$it mm" } ?: "未知"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        RailLength.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { length ->
                    ActionTile(
                        text = "${length.millimeters}",
                        onClick = { onCommand(length.command) },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        selected = state.travelMm == length.millimeters,
                        height = 50.dp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    SectionCard(title = "滑轨型号", subtitle = "MODEL") {
        InfoRow(label = "设备上报型号标定值", value = state.railModel?.toString() ?: "--")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "型号预置（A 组 / B 组各三档）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PresetRow(
            labels = listOf("A1", "A2", "A3"),
            commands = listOf(
                SliderCommand.MODEL_PRESET_A1,
                SliderCommand.MODEL_PRESET_A2,
                SliderCommand.MODEL_PRESET_A3,
            ),
            enabled = enabled,
            onCommand = onCommand,
        )
        Spacer(Modifier.height(8.dp))
        PresetRow(
            labels = listOf("B1", "B2", "B3"),
            commands = listOf(
                SliderCommand.MODEL_PRESET_B1,
                SliderCommand.MODEL_PRESET_B2,
                SliderCommand.MODEL_PRESET_B3,
            ),
            enabled = enabled,
            onCommand = onCommand,
        )
    }

    SectionCard(title = "行程限位微调", subtitle = "需在微调模式") {
        Text(
            text = "这四条指令只有在「微调模式」下才生效（对应首页的「微调模式」开关）。" +
                "「按住左移/右移」让设备持续走，「松开」立即停。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionTile(
                text = "按住左移",
                onClick = { onCommand(SliderCommand.MOVE_LEFT_HOLD) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
            )
            ActionTile(
                text = "松开左移",
                onClick = { onCommand(SliderCommand.MOVE_LEFT_RELEASE) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionTile(
                text = "按住右移",
                onClick = { onCommand(SliderCommand.MOVE_RIGHT_HOLD) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
            )
            ActionTile(
                text = "松开右移",
                onClick = { onCommand(SliderCommand.MOVE_RIGHT_RELEASE) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
            )
        }
    }
}

@Composable
private fun MaintenanceSection(
    state: SliderState,
    enabled: Boolean,
    onCommand: (SliderCommand) -> Unit,
) {
    SectionCard(title = "位置与电源", subtitle = "POWER") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionTile(
                text = "位置记忆",
                onClick = { onCommand(SliderCommand.MEMORY_POSITION_ON) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            ActionTile(
                text = if (state.systemOn) "系统待机" else "系统开机",
                onClick = {
                    onCommand(
                        if (state.systemOn) {
                            SliderCommand.SYSTEM_POWER_OFF
                        } else {
                            SliderCommand.SYSTEM_POWER_ON
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionTile(
                text = if (state.keyLocked) "键锁 开" else "键锁 关",
                onClick = { onCommand(SliderCommand.KEY_LOCK) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                selected = state.keyLocked,
                accent = WarningAmber,
            )
            ActionTile(
                text = "电量指示",
                onClick = {
                    onCommand(
                        if (state.batteryIndicatorOn) {
                            SliderCommand.BATTERY_INDICATOR_OFF
                        } else {
                            SliderCommand.BATTERY_INDICATOR_ON
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                selected = state.batteryIndicatorOn,
            )
        }
        Spacer(Modifier.height(8.dp))
        ActionTile(
            text = "关机",
            onClick = { onCommand(SliderCommand.POWER_OFF) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            accent = DangerRed,
        )
    }

    SectionCard(title = "配对与设置", subtitle = "PAIRING") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionTile(
                text = "进入配对模式",
                onClick = { onCommand(SliderCommand.PAIRING_MODE) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
            )
            ActionTile(
                text = "清除配对信息",
                onClick = { onCommand(SliderCommand.CLEAR_PAIRING) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = WarningAmber,
            )
        }
        Spacer(Modifier.height(8.dp))
        ActionTile(
            text = "打开设备自带设置页",
            onClick = { onCommand(SliderCommand.GO_SETUP) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

// ---------------------------------------------------------------- 复用组件

@Composable
private fun PresetRow(
    labels: List<String>,
    commands: List<SliderCommand>,
    enabled: Boolean,
    onCommand: (SliderCommand) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            ActionTile(
                text = label,
                onClick = { onCommand(commands[index]) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    englishLabel: String,
    value: String,
    unit: String,
    enabled: Boolean,
    onDec: () -> Unit,
    onInc: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = englishLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StepperButton(symbol = "−", enabled = enabled, onFire = onDec)

        Box(
            modifier = Modifier.width(88.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = LiveCyan,
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        }

        StepperButton(symbol = "+", enabled = enabled, onFire = onInc)
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onFire: () -> Unit,
) {
    val background = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    HoldRepeatButton(
        onFire = onFire,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        background = background,
        holdDelayMs = 450L,
        repeatEveryMs = 140L,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}
