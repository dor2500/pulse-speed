package com.dorp.pulse

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.activity.compose.LocalActivity
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
import com.dorp.pulse.ui.PingScreen
import com.dorp.pulse.ui.ResultDetailsScreen
import com.dorp.pulse.ui.label
import com.dorp.pulse.ui.ToolDetail
import com.dorp.pulse.ui.ToolId
import com.dorp.pulse.ui.ToolsHub
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.runtime.mutableStateOf
import com.dorp.pulse.ui.ServerPickerSheet
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
    val choices by vm.serverChoices.choices.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val ping by vm.ping.state.collectAsStateWithLifecycle()
    // The tool open inside the Tools tab (null = the grid). Saved by name so it survives rotation.
    var openToolName by rememberSaveable { mutableStateOf<String?>(null) }
    // The history result whose details are open (its time key), null = the list.
    var openResult by rememberSaveable { mutableStateOf<Long?>(null) }
    val openTool = openToolName?.let { n -> ToolId.entries.firstOrNull { it.name == n } }
    val account by vm.cloud.state.collectAsStateWithLifecycle()
    val accountError by vm.cloud.error.collectAsStateWithLifecycle()

    picker.source?.let { src ->
        ServerPickerSheet(
            state = picker,
            current = choices[src],
            onQuery = vm::searchServers,
            onPick = { vm.chooseServer(src, it) },
            onRetry = vm::retryServers,
            onDismiss = vm::closeServerPicker,
        )
    }
    val activity = LocalActivity.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            FloatingNavBar(
                selected = tab,
                items = listOf(
                    NavSpec(Icons.Rounded.Speed, Icons.Outlined.Speed, stringResource(R.string.tab_test)),
                    NavSpec(Icons.Rounded.Handyman, Icons.Outlined.Handyman, stringResource(R.string.tab_tools)),
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
                0 -> TestScreen(
                    state = state,
                    unit = settings.unit,
                    source = settings.source,
                    onSourceChange = { s -> vm.settingsStore.update { it.copy(source = s) } },
                    serverChoice = choices[settings.source],
                    onOpenServers = { vm.openServerPicker(settings.source) },
                    onStart = vm::start,
                    onCancel = vm::cancel,
                    onDetails = { state.resultTime?.let { t -> openResult = t; tab = 2 } },
                    contentPadding = padding,
                )
                1 -> {
                    BackHandler(enabled = openTool != null) { openToolName = null }
                    val pingHost: (String) -> Unit = { host -> openToolName = ToolId.PING.name; vm.ping.start(host) }
                    when (openTool) {
                        null -> ToolsHub(onOpen = { openToolName = it.name }, contentPadding = padding)
                        ToolId.PING -> PingScreen(
                            state = ping,
                            onHostChange = vm.ping::setHost,
                            onModeChange = vm.ping::setMode,
                            onCountChange = vm.ping::setCount,
                            onStart = vm.ping::start,
                            onStop = vm.ping::stop,
                            onForgetRecent = vm.ping::forgetRecent,
                            onRefreshLocal = vm.ping::refreshLocal,
                            onBack = { openToolName = null },
                            contentPadding = padding,
                        )
                        else -> ToolDetail(openTool, vm, onBack = { openToolName = null }, onPing = pingHost, contentPadding = padding)
                    }
                }
                2 -> {
                    val opened = openResult?.let { t -> history.firstOrNull { it.time == t } }
                    BackHandler(enabled = opened != null) { openResult = null }
                    if (opened != null) ResultDetailsScreen(
                        r = opened,
                        unit = settings.unit,
                        onBack = { openResult = null },
                        onShare = {
                            activity?.let { a ->
                                com.dorp.pulse.data.ShareLink.share(
                                    a,
                                    a.getString(R.string.share_text,
                                        "${com.dorp.pulse.ui.formatSpeed(opened.downloadMbps, settings.unit)} ${settings.unit.label}",
                                        "${com.dorp.pulse.ui.formatSpeed(opened.uploadMbps, settings.unit)} ${settings.unit.label}",
                                        com.dorp.pulse.ui.formatMs(opened.pingMs), com.dorp.pulse.ui.formatMs(opened.jitterMs)),
                                    com.dorp.pulse.data.ShareLink.build(opened, opened.details?.downSamples.orEmpty(), opened.details?.upSamples.orEmpty()),
                                    a.getString(R.string.share_link),
                                )
                            }
                        },
                        onDelete = { openResult = null; vm.deleteResult(opened.time) },
                        contentPadding = padding,
                    ) else HistoryScreen(
                    items = history,
                    unit = settings.unit,
                    onDelete = vm::deleteResult,
                    onClear = vm::clearHistory,
                    onExportCsv = {
                        val csv = vm.historyStore.exportCsv()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "Pulse Speed Test History")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        activity?.startActivity(Intent.createChooser(intent, "Export History"))
                    },
                    onOpen = { openResult = it },
                    contentPadding = padding,
                )
                }
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
                    account = account,
                    accountError = accountError,
                    onSignIn = { activity?.let(vm::signIn) },
                    onSignOut = vm.cloud::signOut,
                    onDeleteCloud = { activity?.let(vm::deleteAccount) },
                    contentPadding = padding,
                )
            }
        }
    }
}

private class NavSpec(val selectedIcon: ImageVector, val icon: ImageVector, val label: String, val badge: Boolean = false)

/**
 * A compact floating pill navigation bar that hugs its contents (about 288dp wide,
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
            modifier = Modifier.widthIn(max = 320.dp),
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
