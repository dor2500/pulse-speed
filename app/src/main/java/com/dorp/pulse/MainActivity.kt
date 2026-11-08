package com.dorp.pulse

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dorp.pulse.ui.HistoryScreen
import com.dorp.pulse.ui.SettingsScreen
import com.dorp.pulse.ui.TestScreen
import com.dorp.pulse.ui.theme.PulseTheme
import com.dorp.pulse.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settingsStore.settings.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            PulseTheme(settings.theme, settings.dynamicColor) {
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settings.collectAsStateWithLifecycle()
    val history by vm.historyStore.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavItem(tab == 0, Icons.Rounded.Speed, Icons.Outlined.Speed, stringResource(R.string.tab_test)) { tab = 0 }
                NavItem(tab == 1, Icons.Rounded.History, Icons.Outlined.History, stringResource(R.string.tab_history)) { tab = 1 }
                NavItem(tab == 2, Icons.Rounded.Settings, Icons.Outlined.Settings, stringResource(R.string.tab_settings)) { tab = 2 }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { t ->
            when (t) {
                0 -> TestScreen(state, settings.unit, vm::start, vm::cancel, padding)
                1 -> HistoryScreen(history, settings.unit, vm.historyStore::delete, vm.historyStore::clear, padding)
                else -> SettingsScreen(settings, vm.settingsStore::update, padding)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else icon, null) },
        label = { Text(label) },
    )
}
