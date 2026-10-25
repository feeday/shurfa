package com.feeday.ime;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class QingyuImeService extends InputMethodService {
    private LinearLayout root, rows, candidates;
    private HorizontalScrollView candidateScroll;
    private TextView composingLabel;
    private PinyinEngine engine;
    private String composing = "";
    private boolean chinese = true, shifted, symbols, password;
    private boolean dictionaryAvailable = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean repeating;
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            if (!repeating) return;
            backspace();
            handler.postDelayed(this, 65);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        try {
            engine = new PinyinEngine(new InputStreamReader(getAssets().open("pinyin.txt"), StandardCharsets.UTF_8));
        } catch (IOException ex) { dictionaryAvailable = false; }
    }

    @Override public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 24, 39));
        root.setPadding(dp(3), dp(5), dp(3), dp(6));
        composingLabel = new TextView(this);
        composingLabel.setTextColor(Color.rgb(94, 234, 212));
        composingLabel.setTextSize(15);
        composingLabel.setGravity(Gravity.CENTER_VERTICAL);
        composingLabel.setPadding(dp(12), 0, 0, 0);
        root.addView(composingLabel, new LinearLayout.LayoutParams(-1, dp(28)));
        composingLabel.setOnClickListener(v -> commitRaw());
        candidateScroll = new HorizontalScrollView(this);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidates = new LinearLayout(this);
        candidateScroll.addView(candidates);
        root.addView(candidateScroll, new LinearLayout.LayoutParams(-1, dp(46)));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);
        drawKeys(); refreshCandidates();
        return root;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        stopRepeat();
        composing = "";
        shifted = false;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        password = (cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
             variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
             variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
             (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        symbols = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME;
        chinese = !password && !symbols && !(cls == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
             variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_URI));
        if (rows != null) { drawKeys(); refreshCandidates(); }
    }

    @Override public void onFinishInput() {
        stopRepeat(); composing = "";
        if (composingLabel != null) refreshCandidates();
        super.onFinishInput();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        stopRepeat();
        // Finalize visible raw letters when the keyboard is hidden, never carry them to another field.
        commitRaw();
        super.onFinishInputView(finishingInput);
    }

    @Override public void onDestroy() { stopRepeat(); super.onDestroy(); }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart,
            int newEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        if (!composing.isEmpty() && (newStart != candidatesEnd || newEnd != candidatesEnd)) {
            composing = "";
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            refreshCandidates();
        }
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private List<PinyinEngine.Candidate> matches() {
        return engine == null ? Collections.emptyList() : engine.lookup(composing);
    }
    private void refreshCandidates() {
        if (composingLabel == null) return;
        composingLabel.setText(composing.isEmpty()
            ? (password ? "轻语 · 密码模式" : (!dictionaryAvailable ? "词库不可用 · 英文输入" : (chinese ? "轻语 · 拼音" : "轻语 · English")))
            : composing + "  · 点击输入原文");
        candidates.removeAllViews();
        for (PinyinEngine.Candidate c : matches()) {
            Button b = key(c.text, false);
            b.setOnClickListener(v -> select(c));
            candidates.addView(b, new LinearLayout.LayoutParams(-2, -1));
        }
        if (composing.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("离线输入  ·  长按空格切换输入法");
            hint.setTextSize(13);
            hint.setTextColor(Color.rgb(148, 163, 184));
            hint.setGravity(Gravity.CENTER_VERTICAL);
            hint.setPadding(dp(12), 0, 0, 0);
            candidates.addView(hint, new LinearLayout.LayoutParams(-2, -1));
        }
        candidateScroll.scrollTo(0, 0);
    }
    private void select(PinyinEngine.Candidate c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        ic.commitText(c.text, 1);
        composing = composing.substring(c.consumed);
        if (!composing.isEmpty()) ic.setComposingText(composing, 1);
        else ic.finishComposingText();
        ic.endBatchEdit();
        refreshCandidates();
    }
    private void commitRaw() {
        if (composing.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) { ic.commitText(composing, 1); ic.finishComposingText(); }
        composing = ""; refreshCandidates();
    }
    private void type(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (chinese && dictionaryAvailable && !symbols && !password && value.matches("[a-z]")) {
            if (composing.length() >= 64) commitRaw();
            composing += value;
            ic.setComposingText(composing, 1);
            refreshCandidates();
        } else { commitRaw(); ic.commitText(value, 1); }
    }
    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!composing.isEmpty()) {
            composing = composing.substring(0, composing.length() - 1);
            ic.setComposingText(composing, 1);
            if (composing.isEmpty()) ic.finishComposingText();
            refreshCandidates();
        } else {
            CharSequence selected = ic.getSelectedText(0);
            if (selected != null && selected.length() > 0) ic.commitText("", 1);
            else if (android.os.Build.VERSION.SDK_INT >= 24) ic.deleteSurroundingTextInCodePoints(1, 0);
            else {
                CharSequence before = ic.getTextBeforeCursor(2, 0);
                int count = before != null && before.length() == 2 && Character.isSurrogatePair(before.charAt(0), before.charAt(1)) ? 2 : 1;
                ic.deleteSurroundingText(count, 0);
            }
        }
    }
    private void stopRepeat() { repeating = false; handler.removeCallbacks(repeatDelete); }
    private void space() {
        if (!composing.isEmpty()) {
            List<PinyinEngine.Candidate> list = matches();
            if (list.isEmpty()) commitRaw(); else select(list.get(0));
        } else type(" ");
    }
    private void enter() {
        commitRaw();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!sendDefaultEditorAction(true)) {
            EditorInfo info = getCurrentInputEditorInfo();
            if (info != null && info.inputType == InputType.TYPE_NULL) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            } else ic.commitText("\n", 1);
        }
    }
    private String enterLabel() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "换行";
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: return "搜索";
            case EditorInfo.IME_ACTION_SEND: return "发送";
            case EditorInfo.IME_ACTION_GO: return "前往";
            case EditorInfo.IME_ACTION_NEXT: return "下项";
            case EditorInfo.IME_ACTION_DONE: return "完成";
            default: return "换行";
        }
    }
    private Button key(String text, boolean accent) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(18);
        b.setTextColor(accent ? Color.rgb(15, 23, 42) : Color.rgb(241, 245, 249));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent ? Color.rgb(94, 234, 212) : Color.rgb(51, 65, 85));
        bg.setCornerRadius(dp(9));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33ffffff), bg, null));
        return b;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        rows.addView(row, new LinearLayout.LayoutParams(-1, dp(52)));
        return row;
    }
    private Button add(LinearLayout row, String text, float weight, boolean accent, Runnable action) {
        Button b = key(text, accent);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, weight);
        p.setMargins(dp(2), dp(3), dp(2), dp(3)); row.addView(b, p);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void letters(String text) {
        LinearLayout r = row();
        for (char c : text.toCharArray()) {
            String value = String.valueOf(c);
            if (shifted && !chinese) value = value.toUpperCase(Locale.ROOT);
            final String output = value;
            add(r, value, 1, false, () -> type(output));
        }
    }
    private void drawKeys() {
        rows.removeAllViews();
        if (symbols) {
            letters("1234567890"); letters("@#￥%&*()-+"); letters("/：；！？.,=_");
        } else {
            letters("qwertyuiop"); letters("asdfghjkl"); letters("zxcvbnm");
        }
        LinearLayout last = row();
        add(last, symbols ? "ABC" : "123", 1.2f, false, () -> { commitRaw(); symbols = !symbols; drawKeys(); });
        add(last, chinese ? "中" : "EN", 1, false, () -> { commitRaw(); chinese = !chinese && !password; shifted = false; drawKeys(); refreshCandidates(); });
        add(last, shifted ? "⇧" : "↑", .8f, false, () -> { if (!chinese) { shifted = !shifted; drawKeys(); } });
        Button space = add(last, "空格", 2.5f, false, this::space);
        space.setOnLongClickListener(v -> { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); return true; });
        add(last, chinese ? "，" : ",", .8f, false, () -> type(chinese ? "，" : ","));
        Button delete = add(last, "⌫", 1.1f, false, this::backspace);
        delete.setContentDescription("删除，长按连续删除");
        delete.setOnLongClickListener(v -> { repeating = true; handler.post(repeatDelete); return true; });
        delete.setOnTouchListener((v, event) -> { if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) stopRepeat(); return false; });
        add(last, enterLabel(), 1.4f, true, this::enter);
    }
}
