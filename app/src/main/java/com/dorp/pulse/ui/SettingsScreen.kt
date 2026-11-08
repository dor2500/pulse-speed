package com.dorp.pulse.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
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
import com.dorp.pulse.data.AppSettings
import com.dorp.pulse.data.SpeedUnit
import com.dorp.pulse.data.TestLength
import com.dorp.pulse.data.ThemeMode

const val REPO_URL = "https://github.com/dor2500/pulse-speed"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
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

        Section(stringResource(R.string.settings_appearance)) {
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
