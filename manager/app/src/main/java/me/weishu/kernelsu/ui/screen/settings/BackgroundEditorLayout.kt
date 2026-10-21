package me.weishu.kernelsu.ui.screen.settings

/** Keep the preview and editor readable in split windows and with enlarged text. */
internal fun useBackgroundEditorTwoPane(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
    widthDp >= 840f && heightDp >= 480f && fontScale <= 1.35f
