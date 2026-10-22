package me.weishu.kernelsu.ui.screen.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.managerStatusLabel

@Composable
internal fun HomeUiState.lkmStatusLabel(@StringRes labelRes: Int): String = managerStatusLabel(
    status = stringResource(labelRes),
    defaultName = stringResource(R.string.app_name),
    customName = customManagerName,
    syncEnabled = canSyncLkmManagerName,
)
