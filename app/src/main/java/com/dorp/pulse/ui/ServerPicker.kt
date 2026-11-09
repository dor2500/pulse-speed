package com.dorp.pulse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R
import com.dorp.pulse.ServerPickerState
import com.dorp.pulse.engine.ServerOption
import com.dorp.pulse.engine.TestSource

/** The "Server" row under the source chips: shows the current choice and opens the picker. */
@Composable
fun ServerRow(source: TestSource, choice: ServerOption?, enabled: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val canPick = source.selectable && enabled
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = canPick, onClick = onOpen),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (choice == null) Icons.Rounded.AutoAwesome else Icons.Rounded.Dns,
                    null,
                    Modifier.size(18.dp),
                    tint = scheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.server_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    when {
                        !source.selectable -> stringResource(R.string.server_cloudflare_note)
                        choice == null -> stringResource(R.string.server_auto_long)
                        else -> listOf(choice.name, choice.location).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source.selectable) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.server_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canPick) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = if (canPick) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
fun ServerPickerSheet(
    state: ServerPickerState,
    current: ServerOption?,
    onQuery: (String) -> Unit,
    onPick: (ServerOption?) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val source = state.source ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Ookla searches server-side; the other sources filter here.
    val filtered = if (source == TestSource.OOKLA || state.query.isBlank()) state.items
    else state.items.filter { (it.name + " " + it.location).contains(state.query.trim(), ignoreCase = true) }
    // Ookla is already ordered by distance; elsewhere the fastest responders float to the top.
    val shown = if (source == TestSource.OOKLA) filtered
    else filtered.sortedWith(compareBy { state.pings[it.id] ?: Double.MAX_VALUE })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                stringResource(R.string.server_choose),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "${source.title} · ${stringResource(source.description())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text(stringResource(R.string.server_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Clear, null) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    ServerItem(
                        title = stringResource(R.string.server_auto),
                        subtitle = stringResource(R.string.server_auto_desc),
                        trailing = null,
                        selected = current == null,
                        auto = true,
                        onClick = { onPick(null) },
                    )
                }
                when {
                    state.loading && state.items.isEmpty() -> item { Centered { CircularProgressIndicator() } }
                    state.error -> item {
                        Centered {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.server_error), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = onRetry, shape = CircleShape) {
                                    Text(stringResource(R.string.server_retry))
                                }
                            }
                        }
                    }
                    shown.isEmpty() && !state.loading -> item {
                        Centered { Text(stringResource(R.string.server_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    else -> items(shown, key = { it.id }) { option ->
                        val subtitle = buildList {
                            if (option.location.isNotBlank() && option.location != option.name) add(option.location)
                            option.distanceKm?.let { add(stringResource(R.string.server_km, it)) }
                        }.joinToString(" · ")
                        ServerItem(
                            title = option.name,
                            subtitle = subtitle,
                            trailing = { PingBadge(state.pings, option.id) },
                            selected = current?.id == option.id,
                            auto = false,
                            onClick = { onPick(option) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerItem(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)?,
    selected: Boolean,
    auto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when {
                    selected -> Icon(Icons.Rounded.Check, null, tint = scheme.primary)
                    auto -> Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onSurfaceVariant)
                    else -> Icon(Icons.Rounded.Dns, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColorAlpha(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.let {
                Spacer(Modifier.width(8.dp))
                it()
            }
        }
    }
}

@Composable
private fun LocalContentColorAlpha() = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.7f)

@Composable
private fun PingBadge(pings: Map<String, Double?>, id: String) {
    val scheme = MaterialTheme.colorScheme
    when {
        id !in pings -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        pings[id] == null -> Text("—", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        else -> {
            val ms = pings[id]!!
            val (bg, fg) = when {
                ms < 40 -> scheme.primaryContainer to scheme.onPrimaryContainer
                ms < 120 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
            }
            Text(
                "⁦${formatMs(ms)} ${stringResource(R.string.unit_ms)}⁩",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { content() }
}
