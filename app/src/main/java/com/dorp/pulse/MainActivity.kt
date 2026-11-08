package com.dorp.pulse

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.material3.MaterialTheme
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
import com.dorp.pulse.data.localizedContext
import com.dorp.pulse.ui.HistoryScreen
import com.dorp.pulse.ui.SettingsScreen
import com.dorp.pulse.ui.TestScreen
import com.dorp.pulse.ui.theme.PulseTheme
import com.dorp.pulse.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settingsStore.settings.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
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
    val update by vm.update.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            FloatingNavBar(
                selected = tab,
                items = listOf(
                    NavSpec(Icons.Rounded.Speed, Icons.Outlined.Speed, stringResource(R.string.tab_test)),
                    NavSpec(Icons.Rounded.History, Icons.Outlined.History, stringResource(R.string.tab_history)),
                    NavSpec(
                        Icons.Rounded.Settings, Icons.Outlined.Settings, stringResource(R.string.tab_settings),
                        badge = update is UpdateState.Available || update is UpdateState.Ready,
                    ),
                ),
                onSelect = { tab = it },
            )
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
                else -> SettingsScreen(
                    settings = settings,
                    onChange = { transform ->
                        val before = settings.language
                        vm.settingsStore.update(transform)
                        if (vm.settingsStore.settings.value.language != before) activity?.recreate()
                    },
                    update = update,
                    onCheckUpdate = { vm.checkForUpdates() },
                    onDownloadUpdate = vm::downloadUpdate,
                    contentPadding = padding,
                )
            }
        }
    }
}

private class NavSpec(val selectedIcon: ImageVector, val icon: ImageVector, val label: String, val badge: Boolean = false)

/**
 * A compact floating pill navigation bar that hugs its contents (about 216dp wide,
 * 60dp tall), centred, floating 20dp above the system gesture bar. The selected
 * destination gets a small tonal pill behind its icon.
 */
@Composable
private fun FloatingNavBar(selected: Int, items: List<NavSpec>, onSelect: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            Row(
                Modifier
                    .height(60.dp)
                    .padding(horizontal = 8.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEachIndexed { i, item ->
                    NavPill(item, i == selected) { onSelect(i) }
                }
            }
        }
    }
}

@Composable
private fun NavPill(item: NavSpec, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val reveal by animateFloatAsState(if (selected) 1f else 0f, bouncy, label = "reveal")
    val content by animateColorAsState(
        if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        label = "content",
    )
    Box(
        Modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        // Small tonal pill that grows out from the icon when selected.
        Box(
            Modifier
                .size(width = 56.dp, height = 36.dp)
                .graphicsLayer {
                    scaleX = 0.4f + 0.6f * reveal
                    alpha = reveal.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(scheme.secondaryContainer),
        )
        BadgedBox(badge = { if (item.badge) Badge() }) {
            Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = content)
        }
    }
}
