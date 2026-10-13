package com.nuotaike.ntk1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nuotaike.ntk1.ble.ConnectionState
import com.nuotaike.ntk1.protocol.SliderMode
import com.nuotaike.ntk1.protocol.SliderState
import com.nuotaike.ntk1.ui.ParamsScreen
import com.nuotaike.ntk1.ui.RemoteScreen
import com.nuotaike.ntk1.ui.TimerDraft
import com.nuotaike.ntk1.ui.theme.NtkTheme

/**
 * 仅存在于 debug 构建的界面预览宿主。
 *
 * 用途：在没有真机（也就不可能真的连上滑轨）时，用一份样例状态渲染"已连接"的界面，
 * 供开发自检与截图。release 包不包含此类。
 */
class PreviewHostActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent?.getStringExtra(EXTRA_SCREEN) ?: SCREEN_REMOTE

        setContent {
            NtkTheme(darkTheme = true) {
                when (screen) {
                    SCREEN_REMOTE_RUNNING -> Remote(
                        playing = true,
                        fineTune = false,
                    )

                    SCREEN_REMOTE_FINETUNE -> Remote(
                        playing = false,
                        fineTune = true,
                    )

                    SCREEN_REMOTE_PHOTO -> Remote(
                        playing = false,
                        fineTune = false,
                        mode = SliderMode.TIME_LAPSE,
                    )

                    SCREEN_PARAMS -> ParamsScreen(
                        state = sampleState(),
                        connection = ConnectionState.READY,
                        onCommand = {},
                        onBack = {},
                    )

                    else -> Remote(playing = false, fineTune = false)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Remote(
        playing: Boolean,
        fineTune: Boolean,
        mode: SliderMode = SliderMode.VIDEO,
    ) {
        RemoteScreen(
            state = sampleState().copy(playing = playing, mode = mode),
            connection = ConnectionState.READY,
            deviceName = "NTK-1",
            runIntent = false,
            fineTune = fineTune,
            onCommand = {},
            onNudgeSpeed = {},
            onSetPhotoTarget = {},
            onSetTimer = { _, _, _ -> },
            onNudgeTimer = { _, _ -> },
            onToggleTimer = {},
            timerDisplay = TimerDraft(
                sampleState().timerHours ?: 0,
                sampleState().timerMinutes ?: 0,
                sampleState().timerSeconds ?: 0,
            ),
            timerDraftActive = false,
            onToggleRepeat = {},
            onToggleRun = {},
            onToggleFineTune = {},
            onPressDirection = {},
            onReleaseDirection = {},
            onOpenSettings = {},
            onOpenDevice = {},
        )
    }

    companion object {
        const val EXTRA_SCREEN = "extra_screen"
        const val SCREEN_REMOTE = "remote"
        const val SCREEN_REMOTE_RUNNING = "remote_running"
        const val SCREEN_REMOTE_FINETUNE = "remote_finetune"
        const val SCREEN_REMOTE_PHOTO = "remote_photo"
        const val SCREEN_PARAMS = "params"

        /** 一份典型的"已连接"样例状态，数值取自真机实测。 */
        fun sampleState(): SliderState = SliderState(
            firmware = 3,
            mode = SliderMode.VIDEO,
            playing = false,
            directionForward = true,
            speedPercent = 15.5,
            positionMm = 88,
            travelMm = 146,
            shotsDone = 0,
            photoTarget = 101,
            photosRemaining = 35,
            repeatRaw = 0,
            delaySetting = 3,
            delaySeconds = 3,
            stabilizationSeconds = 0.5,
            timerHours = 0,
            timerMinutes = 0,
            timerSeconds = 9,
            timerKnown = true,
            timerOn = false,
            batteryVolts = 5.14,
            railModel = 32,
            keyLocked = false,
            systemOn = true,
            batteryIndicatorOn = false,
            sleeping = false,
            memoryPosition = 0,
            updatedAt = System.currentTimeMillis(),
        )
    }
}
