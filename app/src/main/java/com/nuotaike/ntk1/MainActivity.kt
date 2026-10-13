package com.nuotaike.ntk1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuotaike.ntk1.ui.AppRoot
import com.nuotaike.ntk1.ui.SliderViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NtkApp).container
        val initialTab = intent?.getIntExtra(EXTRA_INITIAL_TAB, TAB_REMOTE) ?: TAB_REMOTE

        setContent {
            val viewModel: SliderViewModel = viewModel(
                factory = SliderViewModel.factory(container),
            )
            AppRoot(viewModel = viewModel, initialTab = initialTab)
        }
    }

    companion object {
        /** 指定启动时打开哪个页签，便于做桌面快捷方式直达某个功能页。 */
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"

        const val TAB_REMOTE = 0
        const val TAB_PARAMS = 1
        const val TAB_DEVICE = 3
    }
}
