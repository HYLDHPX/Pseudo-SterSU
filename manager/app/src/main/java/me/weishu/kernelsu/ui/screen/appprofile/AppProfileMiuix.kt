package me.weishu.kernelsu.ui.screen.appprofile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.miuix.DropdownItem
import me.weishu.kernelsu.ui.component.profile.AppProfileConfig
import me.weishu.kernelsu.ui.component.profile.RootProfileConfig
import me.weishu.kernelsu.ui.component.profile.TemplateConfig
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.component.liquid.globalLiquidGlassSurface
import me.weishu.kernelsu.ui.component.liquid.liquidGlassMiuixCardColors
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.immersiveTopBarColor
import me.weishu.kernelsu.ui.theme.uiPassthroughSurfaceColor
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.listAppProfileTemplates
import me.weishu.kernelsu.ui.util.ownerNameForUid
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import me.weishu.kernelsu.ui.component.passthrough.MiuixIcon as Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import me.weishu.kernelsu.ui.component.passthrough.MiuixText as Text
import me.weishu.kernelsu.ui.component.AdaptiveTopAppBar as TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import me.weishu.kernelsu.ui.component.miuix.SunMoonSwitchPreference as SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2023/5/16.
 */
@Composable
fun AppProfileScreenMiuix(
    state: AppProfileUiState,
    actions: AppProfileActions,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = immersiveTopBarColor(
        if (blurActive) Color.Transparent else colorScheme.surface,
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopBar(
                onBack = actions.onBack,
                showActions = !state.isUidGroup && !state.appGroup.primary.isWebViewZygote,
                packageName = state.packageName,
                userId = state.uid / 100000,
                onLaunchApp = actions.onLaunchApp,
                onForceStopApp = actions.onForceStopApp,
                onRestartApp = actions.onRestartApp,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .imePadding()
                    .padding(top = 16.dp)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                item {
                    AppProfileInner(
                        packageName = if (state.isUidGroup) {
                            ""
                        } else {
                            state.appGroup.primary.displayIdentifier
                        },
                        appLabel = if (state.isUidGroup) ownerNameForUid(state.appGroup.primary.uid) else state.appGroup.primary.label,
                        appIcon = {
                            AppIconImage(
                                packageInfo = state.appGroup.primary.packageInfo,
                                label = state.appGroup.primary.label,
                                modifier = Modifier.size(48.dp)
                            )
                        },
                        appUid = state.uid,
                        sharedUserId = if (state.isUidGroup) state.sharedUserId else "",
                        appVersionName = if (state.isUidGroup) "" else (state.appGroup.primary.packageInfo.versionName ?: ""),
                        appVersionCode = if (state.isUidGroup) 0L else state.appGroup.primary.packageInfo.longVersionCode,
                        profile = state.profile,
                        isUidGroup = state.isUidGroup,
                        isSpecialApp = state.appGroup.primary.special,
                        affectedApps = state.appGroup.apps,
                        onViewTemplate = actions.onViewTemplate,
                        onManageTemplate = actions.onManageTemplate,
                        onProfileChange = actions.onProfileChange,
                    )
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AppProfileInner(
    modifier: Modifier = Modifier,
    packageName: String,
    appLabel: String,
    appIcon: @Composable (() -> Unit),
    appUid: Int,
    sharedUserId: String = "",
    appVersionName: String,
    appVersionCode: Long,
    profile: Natives.Profile,
    isUidGroup: Boolean = false,
    isSpecialApp: Boolean = false,
    affectedApps: List<AppInfo> = emptyList(),
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit,
) {
    val isRootGranted = !isSpecialApp && profile.allowSu
    val userId = appUid / 100000
    val appId = appUid % 100000
    val templates = remember { listAppProfileTemplates() }

    Column(
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .profileLiquidGlassSurface(),
            colors = liquidGlassMiuixCardColors(),
            insideMargin = PaddingValues(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                appIcon()
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = appLabel,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight(550),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSpecialApp) {
                        Text(
                            text = packageName,
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.basicMarquee(),
                            maxLines = 1,
                            softWrap = false
                        )
                    } else if (!isUidGroup) {
                        Text(
                            text = "$appVersionName ($appVersionCode)",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .basicMarquee(),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = packageName,
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .basicMarquee(),
                            maxLines = 1,
                            softWrap = false
                        )
                    } else {
                        if (sharedUserId.isNotEmpty()) {
                            Text(
                                text = sharedUserId,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .basicMarquee(),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = stringResource(R.string.group_contains_apps, affectedApps.size),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .basicMarquee(),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    FlowRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (userId != 0) {
                            StatusTag(
                                label = "USER $userId",
                                backgroundColor = colorScheme.secondaryVariant,
                                contentColor = colorScheme.onSecondaryVariant,
                            )
                        }
                        StatusTag(
                            label = "UID ${if (userId != 0) appId else appUid}",
                            backgroundColor = colorScheme.secondaryVariant,
                            contentColor = colorScheme.onSecondaryVariant,
                        )
                    }
                }
            }
        }

        if (!isSpecialApp) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .profileLiquidGlassSurface(),
                colors = liquidGlassMiuixCardColors(),
            ) {
                SwitchPreference(
                    startAction = {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                            tint = colorScheme.onBackground
                        )
                    },
                    title = stringResource(id = R.string.superuser),
                    checked = isRootGranted,
                    onCheckedChange = { onProfileChange(profile.copy(allowSu = it)) },
                )
            }
        }

        val initialRootMode = if (profile.rootUseDefault) {
            Mode.Default
        } else if (profile.rootTemplate != null) {
            Mode.Template
        } else {
            Mode.Custom
        }
        var rootMode by rememberSaveable(profile.rootUseDefault, profile.rootTemplate) {
            mutableStateOf(initialRootMode)
        }
        val nonRootMode = if (profile.nonRootUseDefault) Mode.Default else Mode.Custom
        val dropdownMode = if (isRootGranted) rootMode else nonRootMode
        ProfileBox(dropdownMode, isRootGranted) { mode ->
            if (isRootGranted) {
                when (mode) {
                    Mode.Default, Mode.Custom -> {
                        onProfileChange(
                            profile.copy(
                                rootUseDefault = mode == Mode.Default,
                                rootTemplate = null
                            )
                        )
                        rootMode = mode
                    }

                    Mode.Template -> {
                        if (templates.isNotEmpty()) {
                            val selected = profile.rootTemplate ?: templates[0]
                            val info = me.weishu.kernelsu.ui.viewmodel.getTemplateInfoById(selected)
                            if (info != null) {
                                onProfileChange(
                                    profile.copy(
                                        rootUseDefault = false,
                                        rootTemplate = selected,
                                        uid = info.uid,
                                        gid = info.gid,
                                        groups = info.groups,
                                        capabilities = info.capabilities,
                                        context = info.context,
                                        namespace = info.namespace,
                                    )
                                )
                            }
                        }
                        rootMode = Mode.Template
                    }
                }
            } else {
                onProfileChange(profile.copy(nonRootUseDefault = (mode == Mode.Default)))
            }
        }
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = isRootGranted && rootMode != Mode.Default,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = if (rootMode != Mode.Default) 12.dp else 0.dp)
                    .profileLiquidGlassSurface(),
                colors = liquidGlassMiuixCardColors(),
            ) {
                AnimatedVisibility(
                    visible = rootMode == Mode.Template,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TemplateConfig(
                        profile = profile,
                        onViewTemplate = onViewTemplate,
                        onManageTemplate = onManageTemplate,
                        onProfileChange = onProfileChange
                    )
                }
                AnimatedVisibility(
                    visible = rootMode == Mode.Custom,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    RootProfileConfig(
                        fixedName = true,
                        enabled = rootMode == Mode.Custom,
                        profile = profile,
                        onProfileChange = onProfileChange
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = !isRootGranted && nonRootMode == Mode.Custom,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = if (nonRootMode != Mode.Default) 12.dp else 0.dp)
                    .profileLiquidGlassSurface(),
                colors = liquidGlassMiuixCardColors(),
            ) {
                AnimatedVisibility(
                    visible = nonRootMode == Mode.Custom,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    AppProfileConfig(
                        fixedName = true,
                        profile = profile,
                        enabled = true,
                        onProfileChange = onProfileChange
                    )
                }
            }
        }

        if (isUidGroup) {
            SmallTitle(
                text = stringResource(R.string.app_profile_affects_following_apps),
                modifier = Modifier.padding(top = 4.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .profileLiquidGlassSurface(),
                colors = liquidGlassMiuixCardColors(),
            ) {
                Spacer(Modifier.height(3.dp))
                affectedApps.forEach { app ->
                    BasicComponent(
                        startAction = {
                            AppIconImage(
                                packageInfo = app.packageInfo,
                                label = app.label,
                                modifier = Modifier
                                    .padding(end = 1.dp)
                                    .size(48.dp)
                            )
                        },
                        title = app.label,
                        summary = app.displayIdentifier,
                        insideMargin = PaddingValues(start = 11.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    showActions: Boolean = true,
    packageName: String,
    userId: Int,
    onLaunchApp: (String, Int) -> Unit = { _, _ -> },
    onForceStopApp: (String, Int) -> Unit = { _, _ -> },
    onRestartApp: (String, Int) -> Unit = { _, _ -> },
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.profile),
            navigationIcon = {
                IconButton(
                    onClick = onBack
                ) {
                    val layoutDirection = LocalLayoutDirection.current
                    Icon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        contentDescription = null,
                        tint = colorScheme.onBackground
                    )
                }
            },
            actions = {
                if (showActions) {
                    val showTopPopup = remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showTopPopup.value = true },
                        holdDownState = showTopPopup.value
                    ) {
                        Icon(
                            imageVector = MiuixIcons.MoreCircle,
                            tint = colorScheme.onSurface,
                            contentDescription = stringResource(id = R.string.settings)
                        )
                    }
                    OverlayListPopup(
                        show = showTopPopup.value,
                        popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        onDismissRequest = { showTopPopup.value = false },
                        content = {
                            ListPopupColumn {
                                val items = listOf(
                                    stringResource(id = R.string.launch_app),
                                    stringResource(id = R.string.force_stop_app),
                                    stringResource(id = R.string.restart_app)
                                )

                                items.forEachIndexed { index, text ->
                                    DropdownItem(
                                        text = text,
                                        optionSize = items.size,
                                        index = index,
                                        onSelectedIndexChange = { selectedIndex ->
                                            when (selectedIndex) {
                                                0 -> onLaunchApp(packageName, userId)
                                                1 -> onForceStopApp(packageName, userId)
                                                2 -> onRestartApp(packageName, userId)
                                            }
                                            showTopPopup.value = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun ProfileBox(
    mode: Mode,
    hasTemplate: Boolean,
    onModeChange: (Mode) -> Unit,
) {
    val defaultText = stringResource(R.string.profile_default)
    val templateText = stringResource(R.string.profile_template)
    val customText = stringResource(R.string.profile_custom)
    val modesAndTitles = remember(hasTemplate, defaultText, templateText, customText) {
        buildList {
            add(Mode.Default to defaultText)
            if (hasTemplate) {
                add(Mode.Template to templateText)
            }
            add(Mode.Custom to customText)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .profileLiquidGlassSurface(),
        colors = liquidGlassMiuixCardColors(),
    ) {
        BasicComponent(
            title = stringResource(R.string.profile),
            summary = mode.text,
            startAction = {
                Icon(
                    Icons.Rounded.AccountCircle,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = null,
                    tint = colorScheme.onSurface
                )
            },
        )
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            val stackOptions = maxWidth < (modesAndTitles.size * 88 * LocalDensity.current.fontScale).dp
            if (stackOptions) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modesAndTitles.forEach { (option, title) ->
                        ProfileModeOption(title, mode == option, { onModeChange(option) }, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    modesAndTitles.forEach { (option, title) ->
                        ProfileModeOption(title, mode == option, { onModeChange(option) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileModeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val background = if (selected) colorScheme.primary else uiPassthroughSurfaceColor(colorScheme.surfaceContainerHigh)
    val content = if (selected) colorScheme.onPrimary else colorScheme.onSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = content,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Modifier.profileLiquidGlassSurface(): Modifier {
    return globalLiquidGlassSurface(
        surfaceAlpha = 0.68f,
        blurRadius = 8.dp,
        strokeAlpha = 0.72f,
    )
}
