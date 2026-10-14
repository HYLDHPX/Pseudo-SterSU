package me.weishu.kernelsu.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.stealth.STEALTH_RECOVERY_URL
import me.weishu.kernelsu.stealth.StealthCodeValidation
import me.weishu.kernelsu.stealth.StealthModeStore
import me.weishu.kernelsu.stealth.StealthRecoveryPreflight
import me.weishu.kernelsu.ui.component.ApkeSecondaryScaffold
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

@Composable
fun StealthRecoveryCheckScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf(StealthModeStore.code(context)) }
    var confirmedCode by remember { mutableStateOf("") }
    var webOnlyAcknowledged by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<StealthRecoveryPreflight?>(null) }
    var checking by remember { mutableStateOf(true) }
    var checkAttempt by remember { mutableIntStateOf(0) }
    val validation = StealthModeStore.validateCode(code)
    val dialerCode = StealthModeStore.secretCodeHost(code)
    val dialerLikelyUsable = dialerCode != null && dialerCode.length in 1..20 &&
        dialerCode.all { it in '0'..'9' }

    LaunchedEffect(checkAttempt) {
        checking = true
        checks = null
        checks = withContext(Dispatchers.IO) {
            StealthModeStore.checkRecoveryChannel(context.applicationContext)
        }
        checking = false
    }

    ApkeSecondaryScaffold(
        title = stringResource(R.string.stealth_recovery_check_title),
        onBack = dropUnlessResumed { navigator.pop() },
        maxContentWidth = 680.dp,
    ) { innerPadding, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.stealth_recovery_check_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecoveryCheckRow(R.string.stealth_recovery_check_root, checks?.rootShellAvailable)
                RecoveryCheckRow(R.string.stealth_recovery_check_ksud, checks?.recoveryCommandAvailable)
                RecoveryCheckRow(R.string.stealth_recovery_check_storage, checks?.dataDirectoryWritable)
                RecoveryCheckRow(R.string.stealth_recovery_check_port, checks?.portAvailable)
                RecoveryCheckRow(R.string.stealth_recovery_check_inactive, checks?.modeDisabled)
            }
            Text(
                text = stringResource(
                    when {
                        checking -> R.string.stealth_recovery_check_running
                        checks?.ready == true -> R.string.stealth_recovery_check_ready
                        else -> R.string.stealth_recovery_check_failed
                    }
                ),
                color = if (!checking && checks?.ready == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { checkAttempt++ },
                enabled = !checking && !uiState.stealthModeBusy,
            ) {
                Text(stringResource(R.string.stealth_recovery_check_retry))
            }
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    confirmedCode = ""
                },
                label = { Text(stringResource(R.string.stealth_mode_code_title)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = validation != StealthCodeValidation.Valid,
                supportingText = {
                    Text(stringResource(when (validation) {
                        StealthCodeValidation.Valid -> R.string.stealth_mode_code_format
                        StealthCodeValidation.Invalid -> R.string.stealth_mode_code_invalid
                        StealthCodeValidation.TooLong -> R.string.stealth_mode_code_too_long
                    }))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirmedCode,
                onValueChange = { confirmedCode = it },
                label = { Text(stringResource(R.string.stealth_recovery_confirm_code)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = confirmedCode.isNotEmpty() && confirmedCode != code,
                supportingText = {
                    if (confirmedCode.isNotEmpty() && confirmedCode != code) {
                        Text(stringResource(R.string.stealth_recovery_code_mismatch))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!dialerLikelyUsable) {
                Text(
                    text = stringResource(R.string.stealth_recovery_check_dialer_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = webOnlyAcknowledged,
                        onCheckedChange = { webOnlyAcknowledged = it },
                    )
                    Text(stringResource(R.string.stealth_recovery_web_only_ack))
                }
            }
            Text(
                text = stringResource(R.string.stealth_recovery_address),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(STEALTH_RECOVERY_URL, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SterSU recovery address", STEALTH_RECOVERY_URL))
                Toast.makeText(context, R.string.stealth_recovery_address_copied, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Text(
                    text = stringResource(R.string.stealth_recovery_copy_address),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.stealth_recovery_check_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { viewModel.setStealthMode(true, code) },
                enabled = checks?.ready == true && validation == StealthCodeValidation.Valid &&
                    confirmedCode == code && (dialerLikelyUsable || webOnlyAcknowledged) &&
                    !checking && !uiState.stealthModeBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.stealthModeBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.stealth_mode_enable_action))
            }
        }
    }
}

@Composable
private fun RecoveryCheckRow(label: Int, passed: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        if (passed == null) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Icon(
                imageVector = if (passed) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(
                    if (passed) R.string.stealth_recovery_check_passed
                    else R.string.stealth_recovery_check_not_ready
                ),
                tint = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
