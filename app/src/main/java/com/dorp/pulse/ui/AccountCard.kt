package com.dorp.pulse.ui

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dorp.pulse.R
import com.dorp.pulse.data.AccountError
import com.dorp.pulse.data.AccountState
import com.dorp.pulse.data.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun AccountCard(
    account: AccountState,
    error: AccountError?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteCloud: () -> Unit,
) {
    if (account == AccountState.Unavailable) return
    val scheme = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (account) {
                    is AccountState.SignedIn -> Avatar(account.photoUrl, account.name ?: account.email)
                    else -> Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Cloud, null, tint = scheme.onSecondaryContainer) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (account) {
                            is AccountState.SignedIn -> account.name ?: account.email.orEmpty()
                            else -> stringResource(R.string.account_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (account) {
                            is AccountState.SignedIn -> account.email.orEmpty()
                            else -> stringResource(R.string.account_pitch)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (account is AccountState.SignedIn) {
                Spacer(Modifier.height(14.dp))
                SyncLine(account)
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.errorContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = scheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            when (error) {
                                AccountError.NO_GOOGLE_PLAY -> R.string.account_err_play
                                AccountError.NO_ACCOUNT -> R.string.account_err_no_account
                                AccountError.FAILED -> R.string.account_err_failed
                            }
                        ),
                        color = scheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (account) {
                    is AccountState.SignedIn -> {
                        OutlinedButton(onClick = onSignOut, shape = CircleShape) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.account_sign_out))
                        }
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.account_delete_cloud), color = scheme.error)
                        }
                    }
                    AccountState.SigningIn -> Button(onClick = {}, enabled = false, shape = CircleShape) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.account_signing_in))
                    }
                    else -> Button(onClick = onSignIn, shape = CircleShape) {
                        Icon(Icons.Rounded.Login, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.account_sign_in))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteCloud() }) {
                    Text(stringResource(R.string.account_delete_confirm), color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SyncLine(account: AccountState.SignedIn) {
    val scheme = MaterialTheme.colorScheme
    val (icon, text, tint) = when (account.sync) {
        SyncStatus.SYNCING -> Triple(Icons.Rounded.CloudSync, stringResource(R.string.sync_syncing), scheme.onSurfaceVariant)
        SyncStatus.SYNCED -> Triple(
            Icons.Rounded.CloudDone,
            account.lastSyncedAt?.let {
                if (System.currentTimeMillis() - it < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.sync_synced_now)
                else stringResource(R.string.sync_synced_at, DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
            } ?: stringResource(R.string.sync_synced),
            scheme.primary,
        )
        SyncStatus.OFFLINE -> Triple(Icons.Rounded.CloudOff, stringResource(R.string.sync_offline), scheme.onSurfaceVariant)
        SyncStatus.ERROR -> Triple(Icons.Rounded.ErrorOutline, stringResource(R.string.sync_error), scheme.error)
    }
    Row(
        Modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Google profile photo, or the first letter of the name while it loads or if there is none. */
@Composable
private fun Avatar(url: String?, name: String?) {
    val scheme = MaterialTheme.colorScheme
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let {
            withContext(Dispatchers.IO) {
                runCatching { URL(it).openStream().use { s -> BitmapFactory.decodeStream(s)?.asImageBitmap() } }.getOrNull()
            }
        }
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(scheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = image
        if (bmp != null) Image(bmp, null, Modifier.size(44.dp), contentScale = ContentScale.Crop)
        else Text(
            name?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onPrimary,
        )
    }
}
