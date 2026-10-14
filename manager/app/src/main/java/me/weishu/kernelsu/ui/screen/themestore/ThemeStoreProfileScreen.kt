package me.weishu.kernelsu.ui.screen.themestore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.rememberCustomImageBitmap
import me.weishu.kernelsu.ui.component.store.StoreScaffold
import me.weishu.kernelsu.ui.component.store.StoreSectionHeading
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.FULL_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.LinkedGitHubProfile
import me.weishu.kernelsu.ui.util.ThemeAuthorProfile
import me.weishu.kernelsu.ui.util.isValidCloudThemeGithubLogin
import me.weishu.kernelsu.ui.util.readLinkedGitHubProfile
import me.weishu.kernelsu.ui.util.readThemeAuthorProfile
import me.weishu.kernelsu.ui.util.readThemeLibrary
import me.weishu.kernelsu.ui.util.syncThemeAuthorProfileFromGitHub

private const val GITHUB_PROFILE_REFRESH_MS = 15L * 60L * 1000L

@Composable
fun ThemeStoreMyScreen(
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(readThemeAuthorProfile(context)) }
    var linkedAccount by remember { mutableStateOf(readLinkedGitHubProfile(context)) }
    var savedThemeCount by remember { mutableIntStateOf(readThemeLibrary(context).size) }
    var changingAccount by rememberSaveable { mutableStateOf(false) }
    var githubLoginInput by rememberSaveable { mutableStateOf(linkedAccount?.login.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun syncProfile(login: String) {
        if (busy || !isValidCloudThemeGithubLogin(login)) return
        busy = true
        errorMessage = null
        scope.launch {
            try {
                profile = syncThemeAuthorProfileFromGitHub(context, login)
                linkedAccount = readLinkedGitHubProfile(context)
                githubLoginInput = linkedAccount?.login.orEmpty()
                changingAccount = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errorMessage = error.localizedMessage?.lineSequence()?.firstOrNull()?.take(160)
                    ?: resources.getString(R.string.theme_store_github_sync_failed)
            } finally {
                busy = false
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        profile = readThemeAuthorProfile(context)
        linkedAccount = readLinkedGitHubProfile(context)
        savedThemeCount = readThemeLibrary(context).size
        onPauseOrDispose { }
    }
    LaunchedEffect(linkedAccount?.login) {
        linkedAccount?.takeIf {
            System.currentTimeMillis() - it.syncedAt >= GITHUB_PROFILE_REFRESH_MS
        }?.let { syncProfile(it.login) }
    }

    val content: @Composable (PaddingValues) -> Unit = { paddingValues ->
        ThemeStoreProfileContent(
            profile = profile,
            linkedAccount = linkedAccount,
            savedThemeCount = savedThemeCount,
            changingAccount = changingAccount,
            githubLoginInput = githubLoginInput,
            busy = busy,
            errorMessage = errorMessage,
            modifier = modifier.padding(paddingValues),
            onLoginChange = { githubLoginInput = it.take(39) },
            onSync = { syncProfile(githubLoginInput.trim()) },
            onChangeAccount = {
                githubLoginInput = linkedAccount?.login.orEmpty()
                errorMessage = null
                changingAccount = true
            },
            onCancelChange = {
                changingAccount = false
                errorMessage = null
            },
            onRefresh = { linkedAccount?.let { syncProfile(it.login) } },
            onOpenLibrary = dropUnlessResumed { navigator.push(Route.ThemeStoreLibrary) },
            onOpenCreatorCenter = dropUnlessResumed { navigator.push(Route.CloudThemeCreator) },
        )
    }
    if (embedded) {
        content(PaddingValues())
    } else {
        StoreScaffold(
            title = stringResource(R.string.theme_store_my_title),
            onBack = dropUnlessResumed { navigator.pop() },
            maxContentWidth = 720.dp,
            content = content,
        )
    }
}

@Composable
private fun ThemeStoreProfileContent(
    profile: ThemeAuthorProfile,
    linkedAccount: LinkedGitHubProfile?,
    savedThemeCount: Int,
    changingAccount: Boolean,
    githubLoginInput: String,
    busy: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onLoginChange: (String) -> Unit,
    onSync: () -> Unit,
    onChangeAccount: () -> Unit,
    onCancelChange: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCreatorCenter: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StoreSectionHeading(stringResource(R.string.store_redesign_profile)) }
        item {
            if (linkedAccount == null || changingAccount) {
                GitHubProfileAccountForm(
                    value = githubLoginInput,
                    busy = busy,
                    errorMessage = errorMessage,
                    showCancel = linkedAccount != null,
                    onValueChange = onLoginChange,
                    onSync = onSync,
                    onCancel = onCancelChange,
                )
            } else {
                GitHubProfileSummary(
                    profile = profile,
                    login = linkedAccount.login,
                    busy = busy,
                    errorMessage = errorMessage,
                    onRefresh = onRefresh,
                    onChangeAccount = onChangeAccount,
                )
            }
        }
        item { StoreDestination(
            title = stringResource(R.string.theme_store_my_library_title),
            summary = stringResource(R.string.theme_store_my_count, savedThemeCount),
            icon = Icons.Rounded.Palette,
            onClick = onOpenLibrary,
        ) }
        item { StoreDestination(
            title = stringResource(R.string.cloud_theme_creator_title),
            summary = stringResource(R.string.cloud_theme_creator_entry_summary),
            icon = Icons.Rounded.VerifiedUser,
            onClick = onOpenCreatorCenter,
        ) }
        item {
            Text(
                text = stringResource(R.string.theme_store_github_export_notice),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun GitHubProfileAccountForm(
    value: String,
    busy: Boolean,
    errorMessage: String?,
    showCancel: Boolean,
    onValueChange: (String) -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    ProfileSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.theme_store_github_account_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.theme_store_github_account_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !busy,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.theme_store_github_login_label)) },
                prefix = { Text("@") },
                isError = value.isNotBlank() && !isValidCloudThemeGithubLogin(value.trim()),
            )
            if (errorMessage != null) ProfileError(errorMessage)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSync,
                    enabled = !busy && isValidCloudThemeGithubLogin(value.trim()),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(if (busy) R.string.theme_store_github_syncing
                        else R.string.theme_store_github_sync))
                }
                if (showCancel) {
                    OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
            Text(
                text = stringResource(R.string.theme_store_github_public_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GitHubProfileSummary(
    profile: ThemeAuthorProfile,
    login: String,
    busy: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onChangeAccount: () -> Unit,
) {
    ProfileSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeStoreAuthorAvatar(profile.avatarUriString)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName.ifBlank { login },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@$login",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (profile.bio.isNotBlank()) {
                Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
            }
            if (errorMessage != null) ProfileError(errorMessage)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(if (busy) R.string.theme_store_github_syncing
                        else R.string.theme_store_github_refresh))
                }
                OutlinedButton(
                    onClick = onChangeAccount,
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) { Text(stringResource(R.string.theme_store_github_change_account)) }
            }
            Text(
                text = stringResource(R.string.theme_store_github_public_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileError(message: String) {
    Text(
        text = stringResource(R.string.theme_store_github_sync_failed) + ": " + message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun StoreDestination(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ProfileSurface {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun ThemeStoreAuthorAvatar(uriString: String?) {
    val bitmap = rememberCustomImageBitmap(
        uriString = uriString,
        maxSide = 512,
        crop = FULL_CUSTOM_WALLPAPER_CROP,
    )
    Box(
        modifier = Modifier.size(60.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.theme_store_profile_avatar_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ProfileSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}
