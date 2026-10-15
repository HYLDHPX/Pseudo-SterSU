package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.immersivePageColor
import me.weishu.kernelsu.ui.theme.immersiveScrolledTopBarColor
import me.weishu.kernelsu.ui.theme.immersiveTopBarColor
import me.weishu.kernelsu.ui.util.LkmSusfsAction
import me.weishu.kernelsu.ui.util.LkmSusfsStatus
import me.weishu.kernelsu.ui.util.getLkmSusfsStatus
import me.weishu.kernelsu.ui.util.runLkmSusfsAction
import me.weishu.kernelsu.ui.util.addLkmSusfsPath
import me.weishu.kernelsu.ui.util.clearLkmSusfsUname
import me.weishu.kernelsu.ui.util.removeLkmSusfsPath
import me.weishu.kernelsu.ui.util.setLkmSusfsHideMounts
import me.weishu.kernelsu.ui.util.setLkmSusfsLogging
import me.weishu.kernelsu.ui.util.setLkmSusfsUname

private const val PATH_FEATURE = "CONFIG_KSU_SUSFS_SUS_PATH"
private const val LOG_FEATURE = "CONFIG_KSU_SUSFS_ENABLE_LOG"
private const val MOUNT_FEATURE = "CONFIG_KSU_SUSFS_SUS_MOUNT"
private const val UNAME_FEATURE = "CONFIG_KSU_SUSFS_SPOOF_UNAME"

private enum class LkmSusfsPage { Status, Policy, Paths }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LkmSusfsScreen() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(LkmSusfsStatus()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(LkmSusfsPage.Status) }
    var showConfirmation by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf("") }
    var pathInput by remember { mutableStateOf("") }
    var unameRelease by remember { mutableStateOf("") }
    var unameVersion by remember { mutableStateOf("") }
    var unameEdited by remember { mutableStateOf(false) }

    fun syncStatus(current: LkmSusfsStatus) {
        status = current
        if (!unameEdited) {
            unameRelease = current.unameRelease
            unameVersion = current.unameVersion
        }
    }

    LaunchedEffect(refresh) {
        loading = true
        syncStatus(getLkmSusfsStatus())
        loading = false
    }

    fun act(operation: suspend () -> String?, onSuccess: () -> Unit = {}) {
        if (busy || loading) return
        scope.launch {
            busy = true
            actionError = operation().orEmpty()
            if (actionError.isEmpty()) onSuccess()
            syncStatus(getLkmSusfsStatus())
            busy = false
        }
    }

    val canEdit = status.loaded && !busy && !loading && status.error.isBlank()
    val pathAvailable = canEdit && PATH_FEATURE in status.features
    val policyAvailable = canEdit && LOG_FEATURE in status.features
    val mountAvailable = canEdit && MOUNT_FEATURE in status.features
    val unameAvailable = canEdit && UNAME_FEATURE in status.features

    Scaffold(
        containerColor = immersivePageColor(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lkm_susfs_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { refresh++ }, enabled = !busy && !loading) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.susfs_path_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = immersiveTopBarColor(MaterialTheme.colorScheme.background),
                    scrolledContainerColor = immersiveScrolledTopBarColor(MaterialTheme.colorScheme.surface),
                ),
            )
        },
        bottomBar = {
            ShortNavigationBar {
                LkmSusfsPage.entries.forEach { destination ->
                    val label = stringResource(
                        when (destination) {
                            LkmSusfsPage.Status -> R.string.lkm_susfs_nav_status
                            LkmSusfsPage.Policy -> R.string.lkm_susfs_nav_policy
                            LkmSusfsPage.Paths -> R.string.lkm_susfs_nav_paths
                        },
                    )
                    ShortNavigationBarItem(
                        selected = page == destination,
                        onClick = { page = destination },
                        icon = {
                            Icon(
                                when (destination) {
                                    LkmSusfsPage.Status -> Icons.Rounded.Memory
                                    LkmSusfsPage.Policy -> Icons.Rounded.Policy
                                    LkmSusfsPage.Paths -> Icons.Rounded.VisibilityOff
                                },
                                contentDescription = label,
                            )
                        },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loading) CircularProgressIndicator()
                if (status.error.isNotBlank()) {
                    LkmSusfsCard {
                        Text(status.error, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (actionError.isNotBlank()) {
                    LkmSusfsCard {
                        Text(actionError, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (status.policyError.isNotBlank()) {
                    LkmSusfsCard {
                        Text(status.policyError, color = MaterialTheme.colorScheme.error)
                    }
                }
                when (page) {
                    LkmSusfsPage.Status -> {
                        LkmSusfsCard {
                            Text(stringResource(R.string.lkm_susfs_status), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.lkm_susfs_kmi, status.kmi.ifBlank { "—" }))
                            Text(
                                stringResource(
                                    when {
                                        status.loaded -> R.string.lkm_susfs_loaded
                                        status.supported -> R.string.lkm_susfs_ready
                                        else -> R.string.lkm_susfs_unsupported
                                    },
                                ),
                            )
                            if (status.version.isNotBlank()) {
                                Text(stringResource(R.string.susfs_path_version, status.version))
                            }
                            if (status.loaded) {
                                Text(stringResource(R.string.lkm_susfs_feature_count, (status.features.size - 1).coerceAtLeast(0)))
                            }
                            Text(
                                stringResource(
                                    if (status.autoload) R.string.lkm_susfs_autoload_on
                                    else R.string.lkm_susfs_autoload_off,
                                ),
                            )
                            if (status.lastError.isNotBlank()) {
                                Text(status.lastError, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        LkmSusfsCard {
                            Text(stringResource(R.string.lkm_susfs_explanation), style = MaterialTheme.typography.bodyMedium)
                            if (!status.autoload) {
                                Button(
                                    onClick = { showConfirmation = true },
                                    enabled = !busy && !loading && (status.supported || status.loaded),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.lkm_susfs_enable)) }
                            } else {
                                OutlinedButton(
                                    onClick = { act({ runLkmSusfsAction(LkmSusfsAction.Disable) }) },
                                    enabled = !busy && !loading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.lkm_susfs_disable)) }
                            }
                            if (status.loaded && !status.autoload) {
                                Text(
                                    stringResource(R.string.lkm_susfs_disable_note),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    LkmSusfsPage.Policy -> {
                        LkmSusfsCard {
                            Text(stringResource(R.string.lkm_susfs_nav_policy), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.lkm_susfs_policy_intro), style = MaterialTheme.typography.bodySmall)
                            LkmPolicyRow(
                                title = stringResource(R.string.lkm_susfs_logging),
                                summary = stringResource(R.string.lkm_susfs_logging_summary),
                                checked = status.logging,
                                enabled = policyAvailable,
                                onCheckedChange = { enabled -> act({ setLkmSusfsLogging(enabled) }) },
                            )
                            LkmPolicyRow(
                                title = stringResource(R.string.lkm_susfs_hide_mounts),
                                summary = stringResource(R.string.lkm_susfs_hide_mounts_summary),
                                checked = status.hideMounts,
                                enabled = mountAvailable,
                                onCheckedChange = { enabled -> act({ setLkmSusfsHideMounts(enabled) }) },
                            )
                        }
                        LkmSusfsCard {
                            Text(stringResource(R.string.lkm_susfs_uname_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.lkm_susfs_uname_summary), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = unameRelease,
                                onValueChange = { unameRelease = it; unameEdited = true },
                                label = { Text(stringResource(R.string.lkm_susfs_uname_release)) },
                                singleLine = true,
                                enabled = unameAvailable,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = unameVersion,
                                onValueChange = { unameVersion = it; unameEdited = true },
                                label = { Text(stringResource(R.string.lkm_susfs_uname_version)) },
                                singleLine = true,
                                enabled = unameAvailable,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    act({ setLkmSusfsUname(unameRelease, unameVersion) }) {
                                        unameEdited = false
                                    }
                                },
                                enabled = unameAvailable && unameRelease.isNotBlank() && unameVersion.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.lkm_susfs_uname_apply)) }
                            OutlinedButton(
                                onClick = {
                                    act({ clearLkmSusfsUname() }) {
                                        unameEdited = false
                                    }
                                },
                                enabled = unameAvailable && (status.unameRelease.isNotBlank() || unameEdited),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.lkm_susfs_uname_default)) }
                        }
                    }
                    LkmSusfsPage.Paths -> {
                        LkmSusfsCard {
                            Text(stringResource(R.string.lkm_susfs_nav_paths), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.lkm_susfs_paths_summary), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = pathInput,
                                onValueChange = { pathInput = it },
                                label = { Text(stringResource(R.string.lkm_susfs_path_input)) },
                                singleLine = true,
                                enabled = pathAvailable,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    act({ addLkmSusfsPath(pathInput) }) { pathInput = "" }
                                },
                                enabled = pathAvailable && pathInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.lkm_susfs_path_add)) }
                            if (status.paths.isEmpty()) {
                                Text(stringResource(R.string.lkm_susfs_path_empty), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                status.paths.forEach { path ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(path, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        IconButton(
                                            onClick = { act({ removeLkmSusfsPath(path) }) },
                                            enabled = pathAvailable,
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = stringResource(R.string.susfs_path_remove, path),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(stringResource(R.string.lkm_susfs_confirm_title)) },
            text = { Text(stringResource(R.string.lkm_susfs_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    act({ runLkmSusfsAction(LkmSusfsAction.Enable) })
                }) { Text(stringResource(R.string.lkm_susfs_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LkmSusfsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun LkmPolicyRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
