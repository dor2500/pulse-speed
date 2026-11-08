package com.dorp.pulse.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dorp.pulse.BuildConfig
import com.dorp.pulse.R
import com.dorp.pulse.UpdateState
import com.dorp.pulse.data.AppLanguage
import com.dorp.pulse.data.AppSettings
import com.dorp.pulse.update.Updater
import com.dorp.pulse.data.SpeedUnit
import com.dorp.pulse.data.TestLength
import com.dorp.pulse.data.ThemeMode

const val REPO_URL = "https://github.com/dor2500/pulse-speed"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )

        UpdateCard(update, onCheckUpdate, onDownloadUpdate)

        Section(stringResource(R.string.settings_appearance)) {
            Label(stringResource(R.string.settings_language))
            Segmented(
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                    AppLanguage.ENGLISH to "English",
                    AppLanguage.HEBREW to "עברית",
                ),
                selected = settings.language,
                onSelect = { v -> onChange { it.copy(language = v) } },
            )
            Label(stringResource(R.string.settings_theme))
            Segmented(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                selected = settings.theme,
                onSelect = { v -> onChange { it.copy(theme = v) } },
            )
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_desc)) },
                leadingContent = { Icon(Icons.Rounded.Palette, null) },
                trailingContent = {
                    Switch(
                        checked = settings.dynamicColor && dynamicSupported,
                        enabled = dynamicSupported,
                        onCheckedChange = { v -> onChange { it.copy(dynamicColor = v) } },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }

        Section(stringResource(R.string.settings_test)) {
            Label(stringResource(R.string.settings_units))
            Segmented(
                options = listOf(SpeedUnit.MBPS to "Mbps", SpeedUnit.MBYTES to "MB/s"),
                selected = settings.unit,
                onSelect = { v -> onChange { it.copy(unit = v) } },
            )
            Label(stringResource(R.string.settings_duration))
            Segmented(
                options = listOf(
                    TestLength.QUICK to stringResource(R.string.len_quick),
                    TestLength.STANDARD to stringResource(R.string.len_standard),
                    TestLength.LONG to stringResource(R.string.len_long),
                ),
                selected = settings.length,
                onSelect = { v -> onChange { it.copy(length = v) } },
            )
            Spacer(Modifier.height(12.dp))
        }

        Section(stringResource(R.string.settings_about)) {
            val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
                leadingContent = { Icon(Icons.Rounded.Info, null) },
                colors = itemColors,
            )
            ListItem(
                headlineContent = { Text("Cloudflare") },
                supportingContent = { Text(stringResource(R.string.about_engine)) },
                leadingContent = { Icon(Icons.Rounded.Public, null) },
                colors = itemColors,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_source)) },
                supportingContent = { Text(REPO_URL.removePrefix("https://")) },
                leadingContent = { Icon(Icons.Rounded.Code, null) },
                colors = itemColors,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun UpdateCard(update: UpdateState, onCheck: () -> Unit, onDownload: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val highlight = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready
    var needsPermission by remember { mutableStateOf(false) }

    // Once the APK is downloaded, hand it to the installer (or ask for the install permission first).
    LaunchedEffect(update) {
        if (update is UpdateState.Ready) {
            if (Updater.canInstall(context)) Updater.install(context, update.apk)
            else {
                // First update ever: take the user straight to Android's one-time permission switch.
                needsPermission = true
                Updater.openInstallPermission(context)
            }
        }
    }
    // Coming back from the permission screen: try the install again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, update, needsPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && needsPermission &&
                update is UpdateState.Ready && Updater.canInstall(context)
            ) {
                needsPermission = false
                Updater.install(context, update.apk)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
        color = if (highlight) scheme.primaryContainer else scheme.surfaceContainerLow,
        contentColor = if (highlight) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (highlight) scheme.primary else scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate, null,
                        tint = if (highlight) scheme.onPrimary else scheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (update) {
                            is UpdateState.Available -> stringResource(R.string.update_available, update.info.version)
                            is UpdateState.Downloading -> stringResource(R.string.update_available, update.info.version)
                            is UpdateState.Ready -> stringResource(R.string.update_available, update.info.version)
                            else -> stringResource(R.string.updates_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when (update) {
                            UpdateState.Checking -> stringResource(R.string.update_checking)
                            UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
                            UpdateState.Failed -> stringResource(R.string.update_error)
                            is UpdateState.Available -> stringResource(
                                R.string.update_available_desc,
                                String.format(Locale.US, "%.1f", update.info.sizeBytes / 1_048_576.0),
                                BuildConfig.VERSION_NAME,
                            )
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading, (update.progress * 100).toInt())
                            is UpdateState.Ready -> stringResource(
                                if (needsPermission) R.string.update_permission else R.string.update_ready
                            )
                            UpdateState.Idle -> stringResource(R.string.update_current, BuildConfig.VERSION_NAME)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                    )
                }
            }
            if (update is UpdateState.Downloading) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { update.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    strokeCap = StrokeCap.Round,
                )
            }
            if (update !is UpdateState.Downloading) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (update) {
                        is UpdateState.Available -> {
                            Button(onClick = onDownload, shape = CircleShape) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.update_btn_install))
                            }
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.info.pageUrl))) },
                                shape = CircleShape,
                            ) { Text(stringResource(R.string.update_whats_new)) }
                        }
                        is UpdateState.Ready -> Button(
                            onClick = {
                                if (Updater.canInstall(context)) Updater.install(context, update.apk)
                                else {
                                    needsPermission = true
                                    Updater.openInstallPermission(context)
                                }
                            },
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.InstallMobile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_btn_open_installer))
                        }
                        else -> FilledTonalButton(
                            onClick = onCheck,
                            enabled = update != UpdateState.Checking,
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_check))
                        }
                    }
                }
            }
        }
    }
}
