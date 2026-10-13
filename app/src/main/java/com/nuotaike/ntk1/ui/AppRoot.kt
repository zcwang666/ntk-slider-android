package com.nuotaike.ntk1.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuotaike.ntk1.protocol.SliderMode
import com.nuotaike.ntk1.ui.theme.NtkTheme
import kotlinx.coroutines.launch

/** App 内的三个界面；系统返回键在非首页时回到首页。 */
private enum class Screen { Remote, Params, Device }

/** 把外部传入的 tab 序号映射到界面（0=遥控 1=设置 3=设备）。 */
private fun Int.toScreen(): Screen = when (this) {
    1 -> Screen.Params
    3 -> Screen.Device
    else -> Screen.Remote
}

@Composable
fun AppRoot(viewModel: SliderViewModel, initialTab: Int = 0) {
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var screenOrdinal by rememberSaveable { mutableIntStateOf(initialTab.toScreen().ordinal) }
    val screen = Screen.entries[screenOrdinal]

    NtkTheme(darkTheme = darkTheme) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val connection by viewModel.connection.collectAsStateWithLifecycle()
        val devices by viewModel.devices.collectAsStateWithLifecycle()
        val logs by viewModel.logs.collectAsStateWithLifecycle()
        val connectedName by viewModel.connectedName.collectAsStateWithLifecycle()
        val runIntent by viewModel.runIntent.collectAsStateWithLifecycle()
        val fineTune by viewModel.fineTune.collectAsStateWithLifecycle()
        val timerDisplay by viewModel.displayTimer.collectAsStateWithLifecycle()
        val timerDraft by viewModel.timerDraft.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val action = pendingAction
            pendingAction = null
            if (result.values.all { it }) {
                action?.invoke()
            } else {
                scope.launch { snackbarHostState.showSnackbar("需要蓝牙权限才能连接滑轨") }
            }
        }

        val bluetoothEnableLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                scope.launch { snackbarHostState.showSnackbar("蓝牙已开启，可以扫描了") }
            } else {
                scope.launch { snackbarHostState.showSnackbar("请手动开启蓝牙后再扫描") }
            }
        }

        /** 权限 → 蓝牙开关 → 执行动作。 */
        fun withReadyBluetooth(block: () -> Unit) {
            val afterPermission = {
                if (viewModel.isBluetoothEnabled()) {
                    block()
                } else {
                    bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
            if (viewModel.hasPermissions()) {
                afterPermission()
            } else {
                pendingAction = afterPermission
                permissionLauncher.launch(viewModel.requiredPermissions())
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        // 系统返回键：不在首页时回到首页；已在首页则交回系统（退出 App）
        BackHandler(enabled = screen != Screen.Remote) {
            screenOrdinal = Screen.Remote.ordinal
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (screen == Screen.Remote) {
                    ModeBanner(
                        current = state.mode,
                        enabled = connection.isUsable,
                        onSelect = { mode -> viewModel.send(mode.command) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (screen) {
                    Screen.Remote -> RemoteScreen(
                        state = state,
                        connection = connection,
                        deviceName = connectedName,
                        runIntent = runIntent,
                        fineTune = fineTune,
                        onCommand = viewModel::send,
                        onNudgeSpeed = viewModel::nudgeSpeed,
                        onSetPhotoTarget = viewModel::setPhotoTarget,
                        onSetTimer = viewModel::setTimer,
                        onNudgeTimer = viewModel::nudgeTimer,
                        onToggleTimer = viewModel::toggleTimer,
                        timerDisplay = timerDisplay,
                        timerDraftActive = timerDraft != null,
                        onToggleRepeat = viewModel::toggleRepeat,
                        onToggleRun = {
                            if (state.playing || runIntent) {
                                viewModel.stopRun()
                            } else {
                                viewModel.startRun()
                            }
                        },
                        onToggleFineTune = viewModel::toggleFineTune,
                        onPressDirection = viewModel::pressDirection,
                        onReleaseDirection = viewModel::releaseDirection,
                        onOpenSettings = { screenOrdinal = Screen.Params.ordinal },
                        onOpenDevice = { screenOrdinal = Screen.Device.ordinal },
                    )

                    Screen.Params -> ParamsScreen(
                        state = state,
                        connection = connection,
                        onCommand = viewModel::send,
                        onBack = { screenOrdinal = Screen.Remote.ordinal },
                    )

                    Screen.Device -> DeviceScreen(
                        connection = connection,
                        connectedName = connectedName,
                        state = state,
                        devices = devices,
                        logs = logs,
                        bluetoothSupported = viewModel.isBluetoothSupported(),
                        bluetoothEnabled = viewModel.isBluetoothEnabled(),
                        hasPermissions = viewModel.hasPermissions(),
                        darkTheme = darkTheme,
                        onToggleTheme = { darkTheme = !darkTheme },
                        onRequestPermissions = {
                            permissionLauncher.launch(viewModel.requiredPermissions())
                        },
                        onEnableBluetooth = {
                            bluetoothEnableLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            )
                        },
                        onScan = { withReadyBluetooth { viewModel.startScan() } },
                        onStopScan = viewModel::stopScan,
                        onResetLink = viewModel::resetLink,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onCommand = viewModel::send,
                        onSendRaw = viewModel::sendRawHex,
                        onBack = { screenOrdinal = Screen.Remote.ordinal },
                    )
                }
            }
        }
    }
}

/**
 * 底部工作模式条。
 *
 * 原版把工作模式放在首页的一张卡片里；这里挪到底部常驻，
 * 拇指够得着，也让首页腾出空间给位置与运行控制。
 */
@Composable
private fun ModeBanner(
    current: SliderMode,
    enabled: Boolean,
    onSelect: (SliderMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SliderMode.entries.forEach { mode ->
            val selected = current == mode
            val accent = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                            selected -> accent.copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .pointerInput(enabled, mode) {
                        if (enabled) detectTapGestures(onTap = { onSelect(mode) })
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = mode.zh,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        selected -> accent
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = mode.en,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
