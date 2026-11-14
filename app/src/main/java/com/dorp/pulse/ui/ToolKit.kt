package com.dorp.pulse.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R

/** Back arrow + large title, the header every tool screen shares. */
@Composable
fun ToolHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.t_back), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Scrolling column with the tool header, used by every tool except Ping (which has its own layout). */
@Composable
fun ToolScreen(title: String, onBack: () -> Unit, contentPadding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        ToolHeader(title, onBack)
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ToolField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Uri,
    onGo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onGo != null) ImeAction.Go else ImeAction.Next, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); onGo?.invoke() }),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** Full-width Start / Stop pill. */
@Composable
fun RunButton(running: Boolean, startLabel: String, onStart: () -> Unit, onStop: () -> Unit, enabled: Boolean = true, icon: ImageVector = Icons.Rounded.PlayArrow) {
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    Spacer(Modifier.height(14.dp))
    if (running) {
        Button(
            onClick = onStop, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ping_stop)) }
    } else {
        Button(
            onClick = { focus.clearFocus(); onStart() }, enabled = enabled, shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(startLabel) }
    }
}

@Composable
fun ToolProgress(done: Int, total: Int) {
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else done.toFloat() / total },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        strokeCap = StrokeCap.Round,
    )
}

@Composable
fun ToolCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    ) { Column(Modifier.padding(vertical = 8.dp)) { content() } }
}

/** A label/value row; long-press copies the value. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoRow(label: String, value: String?, mono: Boolean = false, icon: ImageVector? = null) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val v = value?.takeIf { it.isNotBlank() } ?: "—"
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = {
                if (value != null) { clipboard.setText(AnnotatedString(value)); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(
            v,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
fun ToolError(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.errorContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = scheme.onErrorContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, color = scheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ToolNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
    )
}

/** Small tonal pill for a value such as "12 ms" or "WPA3". */
@Composable
fun Pill(text: String, strong: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape).background(if (strong) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (strong) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

@Composable
fun RowGap() = Spacer(Modifier.width(8.dp))

val ToolRowArrangement = Arrangement.spacedBy(8.dp)
