package com.nuotaike.ntk1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuotaike.ntk1.ble.ConnectionState
import com.nuotaike.ntk1.domain.TimerPlanner
import com.nuotaike.ntk1.protocol.SliderCommand
import com.nuotaike.ntk1.protocol.SliderMode
import com.nuotaike.ntk1.protocol.SliderState
import com.nuotaike.ntk1.ui.components.ActionTile
import com.nuotaike.ntk1.ui.components.BatteryGauge
import com.nuotaike.ntk1.ui.components.ConnectionDot
import com.nuotaike.ntk1.ui.components.HoldRepeatButton
import com.nuotaike.ntk1.ui.components.PressReleaseButton
import com.nuotaike.ntk1.ui.components.SectionCard
import com.nuotaike.ntk1.ui.components.StatTile
import com.nuotaike.ntk1.ui.theme.DangerRed
import com.nuotaike.ntk1.ui.theme.LiveCyan
import com.nuotaike.ntk1.ui.theme.SuccessGreen
import com.nuotaike.ntk1.ui.theme.WarningAmber

@Composable
fun RemoteScreen(
    state: SliderState,
    connection: ConnectionState,
    deviceName: String?,
    runIntent: Boolean,
    fineTune: Boolean,
    onCommand: (SliderCommand) -> Unit,
    onNudgeSpeed: (Boolean) -> Unit,
    onSetPhotoTarget: (Int) -> Unit,
    onSetTimer: (Int, Int, Int) -> Unit,
    onNudgeTimer: (TimerUnit, Boolean) -> Unit,
    onToggleTimer: () -> Unit,
    timerDisplay: TimerDraft,
    timerDraftActive: Boolean,
    onToggleRepeat: () -> Unit,
    onToggleRun: () -> Unit,
    onToggleFineTune: () -> Unit,
    onPressDirection: (Boolean) -> Unit,
    onReleaseDirection: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = connection.isUsable
    val running = state.playing || runIntent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusStrip(
            connection = connection,
            deviceName = deviceName,
            state = state,
            onRefresh = { onCommand(SliderCommand.QUERY_ALL) },
            onOpenSettings = onOpenSettings,
            onOpenDevice = onOpenDevice,
        )

        if (!enabled) {
            ConnectPrompt(connection = connection, onGoConnect = onOpenDevice)
        }

        PositionCard(state = state)

        TransportControls(
            running = running,
            fineTune = fineTune,
            enabled = enabled,
            mode = state.mode,
            onToggleRun = onToggleRun,
            onToggleFineTune = onToggleFineTune,
            onPressDirection = onPressDirection,
            onReleaseDirection = onReleaseDirection,
            onNudgeSpeed = onNudgeSpeed,
            onCommand = onCommand,
        )

        // 底部区域随工作模式切换：视频 → 定时器；延时/定格/外触 → 相片设置
        if (state.mode == SliderMode.VIDEO) {
            TimerCard(
                state = state,
                enabled = enabled,
                timerDisplay = timerDisplay,
                timerDraftActive = timerDraftActive,
                onCommand = onCommand,
                onSetTimer = onSetTimer,
                onNudgeTimer = onNudgeTimer,
                onToggleTimer = onToggleTimer,
                onToggleRepeat = onToggleRepeat,
            )
        } else {
            PhotoCard(
                state = state,
                enabled = enabled,
                shooting = running,
                onCommand = onCommand,
                onSetPhotoTarget = onSetPhotoTarget,
                onToggleRepeat = onToggleRepeat,
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StatusStrip(
    connection: ConnectionState,
    deviceName: String?,
    state: SliderState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDevice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionDot(connected = connection.isUsable, busy = connection.isBusy)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = deviceName ?: "未连接滑轨",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (deviceName == null && !connection.isUsable) {
                    "点右上角扫描"
                } else {
                    connectionLabel(connection)
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    connection.isUsable -> SuccessGreen
                    connection == ConnectionState.RECONNECTING -> WarningAmber
                    connection == ConnectionState.FAILED -> DangerRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
        BatteryGauge(volts = state.batteryVolts)
        Spacer(Modifier.width(4.dp))
        IconChip(Icons.Filled.Refresh, "刷新状态", onRefresh)
        IconChip(Icons.Filled.Settings, "设置", onOpenSettings)
        IconChip(Icons.Filled.Info, "设备", onOpenDevice)
    }
}

@Composable
private fun IconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun ConnectPrompt(
    connection: ConnectionState,
    onGoConnect: () -> Unit,
) {
    SectionCard(title = "尚未连接滑轨", subtitle = "NTK-1") {
        Text(
            text = when (connection) {
                ConnectionState.SCANNING -> "正在扫描附近的滑轨…"
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING -> "正在建立连接…"
                ConnectionState.RECONNECTING -> "链路中断，正在自动重连…"
                ConnectionState.FAILED -> "连接失败，请确认滑轨已上电并靠近手机"
                else -> "请先到「设备」页扫描并连接控制器，连接后所有控制自动解锁。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ActionTile(
            text = "前往设备页",
            onClick = onGoConnect,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
        )
    }
}

/** 位置：进度条 + 速度/已拍/剩余/延时/往返。 */
@Composable
private fun PositionCard(state: SliderState) {
    SectionCard(title = "位置", subtitle = "PLACE") {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
            color = LiveCyan,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.directionForward) "A → B" else "A ← B",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatTile(
                label = "速度",
                value = state.speedLabel,
                unit = "%",
                accent = WarningAmber,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "已拍",
                value = state.shotsDone.toString(),
                unit = "P",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "剩余",
                value = state.photosRemaining?.toString() ?: "--",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "延时",
                value = state.delayDisplay?.toString() ?: "--",
                unit = "s",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "往返",
                value = state.repeatLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TransportControls(
    running: Boolean,
    fineTune: Boolean,
    enabled: Boolean,
    mode: SliderMode,
    onToggleRun: () -> Unit,
    onToggleFineTune: () -> Unit,
    onPressDirection: (Boolean) -> Unit,
    onReleaseDirection: (Boolean) -> Unit,
    onNudgeSpeed: (Boolean) -> Unit,
    onCommand: (SliderCommand) -> Unit,
) {
    SectionCard(
        title = "运行控制",
        subtitle = if (running) "运行中" else "已停止",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "行程标定",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (fineTune) {
                        "已开启 · 此模式下移动会重新定义行程端点"
                    } else {
                        "方向控制需要先打开它"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fineTune) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ActionTile(
                text = if (fineTune) "关闭" else "打开",
                onClick = onToggleFineTune,
                modifier = Modifier.width(96.dp),
                enabled = enabled,
                selected = fineTune,
                accent = WarningAmber,
                height = 46.dp,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DirectionPad(
                left = true,
                fineTune = fineTune,
                enabled = enabled,
                onPress = onPressDirection,
                onRelease = onReleaseDirection,
            )
            RunToggleButton(running = running, enabled = enabled, onClick = onToggleRun)
            DirectionPad(
                left = false,
                fineTune = fineTune,
                enabled = enabled,
                onPress = onPressDirection,
                onRelease = onReleaseDirection,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 速度 − / 快门 / 速度 ＋
        // 真机实测：这两条调速指令**只在视频模式下生效**，
        // 延时/定格/外触模式下设备完全不响应，所以按模式禁用并给出说明。
        val speedAdjustable = enabled && mode == SliderMode.VIDEO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeedStepButton(
                label = "速度 −",
                enabled = speedAdjustable,
                onFire = { onNudgeSpeed(false) },
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                text = "快门",
                onClick = { onCommand(SliderCommand.SHUTTER) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                height = 60.dp,
            )
            SpeedStepButton(
                label = "速度 ＋",
                enabled = speedAdjustable,
                onFire = { onNudgeSpeed(true) },
                modifier = Modifier.weight(1f),
            )
        }

        if (enabled && mode != SliderMode.VIDEO) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "速度调整仅在视频模式下生效（当前为${mode.zh}模式）",
                style = MaterialTheme.typography.labelSmall,
                color = WarningAmber,
            )
        }
    }
}

/** 速度步进键：单击一步（约 0.5%），长按连续调整。 */
@Composable
private fun SpeedStepButton(
    label: String,
    enabled: Boolean,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HoldRepeatButton(
        onFire = onFire,
        enabled = enabled,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(14.dp),
        holdDelayMs = 500L,
        repeatEveryMs = 120L,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                WarningAmber
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}

/** 启动 / 停止 合一的圆形按钮。 */
@Composable
private fun RunToggleButton(
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (running) DangerRed else SuccessGreen
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(if (enabled) color else color.copy(alpha = 0.3f))
                .pointerInput(enabled, running) {
                    if (enabled) detectTapGestures(onTap = { onClick() })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (running) StopGlyph() else PlayGlyph()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (running) "停止" else "启动",
            style = MaterialTheme.typography.labelMedium,
            color = if (running) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DirectionPad(
    left: Boolean,
    fineTune: Boolean,
    enabled: Boolean,
    onPress: (Boolean) -> Unit,
    onRelease: (Boolean) -> Unit,
) {
    val label = if (left) "左移" else "右移"
    val icon = if (left) {
        Icons.AutoMirrored.Filled.KeyboardArrowLeft
    } else {
        Icons.AutoMirrored.Filled.KeyboardArrowRight
    }
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (fineTune) {
            PressReleaseButton(
                onPress = { onPress(left) },
                onRelease = { onRelease(left) },
                enabled = enabled,
                modifier = Modifier.size(width = 76.dp, height = 76.dp),
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(38.dp))
            }
        } else {
            HoldRepeatButton(
                onFire = { onPress(left) },
                enabled = enabled,
                modifier = Modifier.size(width = 76.dp, height = 76.dp),
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (fineTune) "$label 按住" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 底部：随模式切换

/** 视频模式：定时器（闹钟样式，时/分/秒可直接输入）。 */
@Composable
private fun TimerCard(
    state: SliderState,
    enabled: Boolean,
    timerDisplay: TimerDraft,
    timerDraftActive: Boolean,
    onCommand: (SliderCommand) -> Unit,
    onSetTimer: (Int, Int, Int) -> Unit,
    onNudgeTimer: (TimerUnit, Boolean) -> Unit,
    onToggleTimer: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    val hours = timerDisplay.hours
    val minutes = timerDisplay.minutes
    val seconds = timerDisplay.seconds

    SectionCard(title = "定时器", subtitle = "TIMER") {
        RepeatToggle(state = state, enabled = enabled, onToggleRepeat = onToggleRepeat)

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClockColumn(
                label = "时",
                value = hours,
                enabled = enabled,
                onPlus = { onNudgeTimer(TimerUnit.HOUR, true) },
                onMinus = { onNudgeTimer(TimerUnit.HOUR, false) },
                onCommit = { onSetTimer(it, minutes, seconds) },
                range = 0..TimerPlanner.MAX_HOURS,
                modifier = Modifier.weight(1f),
            )
            ClockColumn(
                label = "分",
                value = minutes,
                enabled = enabled,
                onPlus = { onNudgeTimer(TimerUnit.MINUTE, true) },
                onMinus = { onNudgeTimer(TimerUnit.MINUTE, false) },
                onCommit = { onSetTimer(hours, it, seconds) },
                range = 0..TimerPlanner.MAX_MINUTES,
                modifier = Modifier.weight(1f),
            )
            ClockColumn(
                label = "秒",
                value = seconds,
                enabled = enabled,
                onPlus = { onNudgeTimer(TimerUnit.SECOND, true) },
                onMinus = { onNudgeTimer(TimerUnit.SECOND, false) },
                onCommit = { onSetTimer(hours, minutes, it) },
                range = 0..TimerPlanner.MAX_SECONDS,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionTile(
                text = if (state.timerOn) "关闭定时器" else "开启定时器",
                onClick = onToggleTimer,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                selected = state.timerOn,
                accent = LiveCyan,
                height = 52.dp,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (timerDraftActive) {
                "上方是你调的值。设备只在定时器开启后才回传真实时长，" +
                    "点「开启定时器」即自动对齐为设备实际值。"
            } else {
                "上＋下−逐格调整，中间数字可直接输入（60 秒自动进位）。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (timerDraftActive) {
                WarningAmber
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            },
        )
    }
}

/** 闹钟式的一列：上＋、中间可输入、下−。 */
@Composable
private fun ClockColumn(
    label: String,
    value: Int,
    enabled: Boolean,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onCommit: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
) {
    val shown = "%02d".format(value)
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(shown) }

    LaunchedEffect(shown, editing) {
        if (!editing) text = shown
    }

    fun commit() {
        editing = false
        val parsed = text.toIntOrNull()
        if (parsed != null && parsed in range) {
            onCommit(parsed)
        } else {
            text = shown
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActionTile(
            text = "＋",
            onClick = onPlus,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            height = 40.dp,
        )

        Spacer(Modifier.height(6.dp))

        BasicTextField(
            value = text,
            onValueChange = { input ->
                editing = true
                text = input.filter { it.isDigit() }.take(2)
            },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus -> if (!focus.isFocused && editing) commit() },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = 1.dp,
                            color = if (editing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) { inner() }
            },
        )

        Spacer(Modifier.height(6.dp))

        ActionTile(
            text = "−",
            onClick = onMinus,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            height = 40.dp,
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 延时 / 定格 / 外触模式：相片设置。 */
@Composable
private fun PhotoCard(
    state: SliderState,
    enabled: Boolean,
    shooting: Boolean,
    onCommand: (SliderCommand) -> Unit,
    onSetPhotoTarget: (Int) -> Unit,
    onToggleRepeat: () -> Unit,
) {
    // 未开始拍摄时显示并可编辑"目标张数"；开始拍摄后只显示"剩余张数"
    val shownValue: String = if (shooting) {
        state.photosRemaining?.toString() ?: "--"
    } else {
        state.photoTarget?.toString() ?: "--"
    }

    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(shownValue) }
    var presetsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(shownValue, editing, shooting) {
        if (!editing) text = shownValue
    }

    fun commit() {
        editing = false
        val target = text.toIntOrNull()
        if (target != null && target > 0) {
            onSetPhotoTarget(target)
        } else {
            text = shownValue
        }
    }

    SectionCard(title = "相片设置", subtitle = "PHOTO") {
        RepeatToggle(state = state, enabled = enabled, onToggleRepeat = onToggleRepeat)
        Spacer(Modifier.height(12.dp))

        Text(
            text = if (shooting) "剩余张数" else "目标张数",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：−1（B1）
            ActionTile(
                text = "−",
                onClick = { onCommand(SliderCommand.PHOTO_PRESET_B1) },
                modifier = Modifier.width(72.dp),
                enabled = enabled && !shooting,
                height = 60.dp,
            )

            BasicTextField(
                value = text,
                onValueChange = { input ->
                    editing = true
                    text = input.filter { it.isDigit() }.take(4)
                },
                singleLine = true,
                enabled = enabled && !shooting,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus -> if (!focus.isFocused && editing) commit() },
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = 1.dp,
                                color = if (editing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) { inner() }
                },
            )

            // 右：+1（A1）
            ActionTile(
                text = "＋",
                onClick = { onCommand(SliderCommand.PHOTO_PRESET_A1) },
                modifier = Modifier.width(72.dp),
                enabled = enabled && !shooting,
                height = 60.dp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已拍 ${state.shotsDone} P",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "剩余 ${state.photosRemaining ?: "--"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        ActionTile(
            text = "相片清零",
            onClick = { onCommand(SliderCommand.PHOTO_CLEAR) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            accent = WarningAmber,
            height = 48.dp,
        )

        // 真机实测：设备对目标张数有硬下限（本机 20 张），
        // 低于它时指令照发但设备不执行，这里明确告诉用户，避免以为是 App 卡住。
        if (state.photoTarget != null && state.photoTarget <= PHOTO_TARGET_MIN) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已达设备下限（$PHOTO_TARGET_MIN 张）——再按 − 指令会发出，但设备不再下调。",
                style = MaterialTheme.typography.labelSmall,
                color = WarningAmber,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 张数预置收进下拉，默认收起，避免首屏过长
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickableNoRipple { presetsExpanded = !presetsExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "张数预置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (presetsExpanded) "收起 ▴" else "展开 ▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (presetsExpanded) {
            Spacer(Modifier.height(8.dp))
            PresetRow(
                labels = listOf("+1", "+10", "+100"),
                commands = listOf(
                    SliderCommand.PHOTO_PRESET_A1,
                    SliderCommand.PHOTO_PRESET_A2,
                    SliderCommand.PHOTO_PRESET_A3,
                ),
                enabled = enabled && !shooting,
                onCommand = onCommand,
            )
            Spacer(Modifier.height(8.dp))
            PresetRow(
                labels = listOf("−1", "−10", "−100"),
                commands = listOf(
                    SliderCommand.PHOTO_PRESET_B1,
                    SliderCommand.PHOTO_PRESET_B2,
                    SliderCommand.PHOTO_PRESET_B3,
                ),
                enabled = enabled && !shooting,
                onCommand = onCommand,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "输入框可直接改目标张数；需要 ±10/±100 时展开上面的张数预置。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** 往返开关：紧凑一行，右侧一个小按键。 */
@Composable
private fun RepeatToggle(
    state: SliderState,
    enabled: Boolean,
    onToggleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "往返",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (state.repeatIsOff) "已关闭" else state.repeatLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (state.repeatIsOff) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                SuccessGreen
            },
        )
        Spacer(Modifier.weight(1f))
        ActionTile(
            text = if (state.repeatIsOff) "开启" else "关闭",
            onClick = onToggleRepeat,
            modifier = Modifier.width(64.dp),
            enabled = enabled,
            selected = !state.repeatIsOff,
            accent = LiveCyan,
            height = 34.dp,
        )
    }
}

/** 目标张数的设备下限；真机实测（型号标定 32）为 20 张。 */
private const val PHOTO_TARGET_MIN = 20

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

// ---------------------------------------------------------------- 矢量图形

/** 闹钟图标（对应官方小程序首页那个 time.png）。 */
@Composable
private fun AlarmGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val r = size.minDimension * 0.42f
        val c = Offset(size.width / 2f, size.height / 2f + size.height * 0.06f)
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = size.width * 0.09f))
        // 时针 / 分针
        drawLine(tint, c, Offset(c.x, c.y - r * 0.55f), strokeWidth = size.width * 0.09f)
        drawLine(tint, c, Offset(c.x + r * 0.45f, c.y + r * 0.15f), strokeWidth = size.width * 0.09f)
        // 两侧的铃铛
        drawLine(
            tint,
            Offset(c.x - r * 0.95f, c.y - r * 0.95f),
            Offset(c.x - r * 1.35f, c.y - r * 1.35f),
            strokeWidth = size.width * 0.11f,
        )
        drawLine(
            tint,
            Offset(c.x + r * 0.95f, c.y - r * 0.95f),
            Offset(c.x + r * 1.35f, c.y - r * 1.35f),
            strokeWidth = size.width * 0.11f,
        )
    }
}

@Composable
private fun PlayGlyph() {
    Canvas(Modifier.size(34.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.80f, size.height * 0.50f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            close()
        }
        drawPath(path, color = Color.White)
    }
}

@Composable
private fun StopGlyph() {
    Canvas(Modifier.size(34.dp)) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width * 0.22f, size.height * 0.22f),
            size = Size(size.width * 0.56f, size.height * 0.56f),
            cornerRadius = CornerRadius(size.width * 0.08f),
        )
    }
}

internal fun connectionLabel(connection: ConnectionState): String = when (connection) {
    ConnectionState.IDLE -> "未连接"
    ConnectionState.SCANNING -> "扫描中"
    ConnectionState.CONNECTING -> "连接中"
    ConnectionState.DISCOVERING -> "发现服务"
    ConnectionState.READY -> "已就绪"
    ConnectionState.RECONNECTING -> "重连中"
    ConnectionState.FAILED -> "连接失败"
}

/**
 * 无涟漪点击（用于状态条上的小按钮，避免视觉噪声）。
 *
 * 手势 key 固定为 Unit、回调走 rememberUpdatedState——否则每次重组都会重建
 * pointerInput，触摸期间的刷新会把这次点击吃掉。
 */
@Composable
internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = { currentOnClick() })
    }
}
