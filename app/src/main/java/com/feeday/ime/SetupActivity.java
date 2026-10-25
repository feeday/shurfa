package com.feeday.ime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(17, 24, 39));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(p, p + insets.getSystemWindowInsetTop(), p,
                p + insets.getSystemWindowInsetBottom());
            return insets;
        });
        title(root, "轻语", 36);
        title(root, "轻一点，表达多一点。", 18);
        title(root, "九宫格 / 26 键 · 横竖屏自适应\n基础版 0.2.0", 15);
        Button enable = new Button(this);
        enable.setText("1  启用轻语输入法");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);
        Button choose = new Button(this);
        choose.setText("2  切换到轻语");
        choose.setOnClickListener(v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        root.addView(choose);
        title(root, "3  试打：全键 nihao / 九宫格 64426", 18);
        EditText demo = new EditText(this);
        demo.setHint("在这里试打：你好，世界");
        demo.setTextColor(Color.WHITE);
        demo.setHintTextColor(Color.LTGRAY);
        demo.setMinLines(3);
        demo.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(demo);
        title(root, "使用方法\n键盘右上角可切换九宫格与 26 键，选择会自动保存。九宫格每个字母按一次对应数字键，例如 64426 → 你好；候选附带拼音，可左右滑动并翻页。26 键输入完整拼音。点击候选或按空格选字，点击上方编码提交原文。横屏会自动使用更紧凑的键盘。\n\n长按空格切换系统输入法。长按删除可连续退格。v 代表 ü。\n\n隐私\n应用不申请网络权限，不记录或上传输入内容，不读取剪贴板。密码框自动使用英文模式。\n\n词库说明\n这是基础词库原型，支持常用字词与前缀逐段选字，暂不支持完整智能整句、模糊音、语音或云联想。", 15);
        setContentView(scroll);
    }

    private void title(LinearLayout root, String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.rgb(226, 232, 240));
        label.setPadding(0, 16, 0, 24);
        root.addView(label);
    }
}
