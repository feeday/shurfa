package com.feeday.ime;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class VoicePermissionActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(p, p + insets.getSystemWindowInsetTop(), p, p + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView text = new TextView(this);
        text.setText("语音输入\n\n仅在点击语音按钮后使用麦克风。优先使用设备端识别；设备端不可用时调用系统语音服务，服务提供商可能联网处理音频。\n\n轻语不保存录音。授权后请返回原输入框，再点“语音”。没有安装语音识别服务的设备需要先安装或启用该服务。\n");
        text.setTextSize(18);
        root.addView(text);
        Button allow = new Button(this);
        allow.setText("允许使用麦克风");
        allow.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finish();
            else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        });
        root.addView(allow);
        Button settings = new Button(this);
        settings.setText("权限被拒绝？打开应用设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        root.addView(settings);
        Button back = new Button(this);
        back.setText("返回"); back.setOnClickListener(v -> finish()); root.addView(back);
        setContentView(root);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(this, granted ? "已授权，请返回输入框点击语音" : "未授权，可继续使用文字键盘", Toast.LENGTH_LONG).show();
        if (granted) finish();
    }
}
