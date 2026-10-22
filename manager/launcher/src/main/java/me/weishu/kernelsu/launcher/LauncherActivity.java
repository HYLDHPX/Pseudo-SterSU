package me.weishu.kernelsu.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

/** A permission-free launcher. Root and all Manager state remain in the main app. */
public final class LauncherActivity extends Activity {
    private static final String MANAGER = "io.github.fixz.stersu";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent target = new Intent().setComponent(new ComponentName(MANAGER,
                "me.weishu.kernelsu.ui.util.LauncherEntryActivity"));
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Only this one-use challenge is forwarded; never accept arbitrary targets/extras.
        target.putExtra("launcher_challenge", getIntent().getStringExtra("launcher_challenge"));
        target.putExtra("restore_launcher", "io.github.fixz.stersu.RESTORE_ICON".equals(getIntent().getAction()));
        try {
            startActivity(target);
            finish();
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.manager_unavailable)
                    .setPositiveButton(R.string.app_settings, (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + MANAGER)));
                        } catch (RuntimeException ignored) { }
                        finish();
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        }
    }
}
