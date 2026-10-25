package com.feeday.ime;

import android.graphics.Color;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.res.Configuration;
import android.util.TypedValue;
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
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class QingyuImeService extends InputMethodService {
    private LinearLayout root, panel, rows, candidates, header;
    private HorizontalScrollView candidateScroll;
    private TextView composingLabel;
    private Button layoutToggle;
    private Button voiceButton;
    private String toolPanel = "";
    private boolean selecting;
    private int selectionEnd = -1;
    private SpeechRecognizer recognizer;
    private final VoiceSession voiceSession = new VoiceSession();
    private String voiceStatus = "";
    private final Runnable voiceTimeout = () -> { cancelVoice(); notifyUser("语音已超时，请重试"); };
    private KeyboardSizing sizing;
    private boolean nineKey, changingConfiguration;
    private int panelWidth;
    private static final int PAGE_SIZE = 24;
    private int candidatePage;
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
        nineKey = getSharedPreferences("keyboard", MODE_PRIVATE).getBoolean("nineKey", false);
        try {
            engine = new PinyinEngine(new InputStreamReader(getAssets().open("pinyin.txt"), StandardCharsets.UTF_8));
        } catch (IOException ex) { dictionaryAvailable = false; }
    }

    @Override public View onCreateInputView() {
        sizing = windowSizing();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(17, 24, 39));
        root.setPadding(dp(3), dp(4), dp(3), dp(4));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        root.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        panelWidth = 0;
        root.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            int width = Math.min(right - left - root.getPaddingLeft() - root.getPaddingRight(), dp(sizing.maxWidth));
            if (width > 0 && width != panelWidth) {
                panelWidth = width;
                panel.getLayoutParams().width = width;
                panel.requestLayout();
            }
        });
        header = new LinearLayout(this);
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(sizing.header)));
        composingLabel = new TextView(this);
        composingLabel.setTextColor(Color.rgb(94, 234, 212));
        composingLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizing.compact ? 12 : 14);
        composingLabel.setSingleLine(true);
        composingLabel.setEllipsize(android.text.TextUtils.TruncateAt.START);
        composingLabel.setGravity(Gravity.CENTER_VERTICAL);
        composingLabel.setPadding(dp(12), 0, 0, 0);
        header.addView(composingLabel, new LinearLayout.LayoutParams(0, -1, 1));
        composingLabel.setOnClickListener(v -> commitRaw());
        layoutToggle = key("", false);
        layoutToggle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        layoutToggle.setOnClickListener(v -> toggleLayout());
        header.addView(layoutToggle, new LinearLayout.LayoutParams(dp(80), -1));
        voiceButton = headerButton("语音", 44, this::toggleVoice);
        headerButton("剪贴板", 50, () -> openPanel("clipboard"));
        headerButton("编辑", 42, () -> openPanel("edit"));
        candidateScroll = new HorizontalScrollView(this);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidates = new LinearLayout(this);
        candidateScroll.addView(candidates);
        panel.addView(candidateScroll, new LinearLayout.LayoutParams(-1, dp(sizing.candidates)));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows);
        drawKeys(); refreshCandidates();
        return root;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    private KeyboardSizing windowSizing() {
        Configuration c = getResources().getConfiguration();
        return KeyboardSizing.forWindow(c.screenHeightDp, c.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        stopRepeat();
        cancelVoice();
        changingConfiguration = true;
        try { super.onConfigurationChanged(config); }
        finally { changingConfiguration = false; }
        if (rows != null) { drawKeys(); refreshCandidates(); }
    }

    private boolean usingNineKey() { return nineKey && chinese && !symbols && !password; }

    private void toggleLayout() {
        cancelVoice();
        commitRaw();
        nineKey = !nineKey;
        getSharedPreferences("keyboard", MODE_PRIVATE).edit().putBoolean("nineKey", nineKey).apply();
        drawKeys(); refreshCandidates();
    }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        if (changingConfiguration && restarting) return;
        cancelVoice();
        toolPanel = "";
        selecting = false;
        selectionEnd = info.initialSelEnd;
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
        if (changingConfiguration) return;
        cancelVoice(); toolPanel = ""; selecting = false; selectionEnd = -1;
        if (rows != null) drawKeys();
        stopRepeat(); composing = "";
        if (composingLabel != null) refreshCandidates();
        super.onFinishInput();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        stopRepeat();
        if (changingConfiguration) return;
        cancelVoice(); toolPanel = ""; selecting = false;
        if (rows != null) drawKeys();
        // Finalize visible raw letters when the keyboard is hidden, never carry them to another field.
        commitRaw();
        super.onFinishInputView(finishingInput);
    }

    @Override public void onDestroy() { cancelVoice(); stopRepeat(); super.onDestroy(); }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart,
            int newEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        selectionEnd = newEnd;
        if (recognizer != null && (newStart != oldStart || newEnd != oldEnd)) cancelVoice();
        if (!composing.isEmpty() && (newStart != candidatesEnd || newEnd != candidatesEnd)) {
            composing = "";
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            refreshCandidates();
        }
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private List<PinyinEngine.Candidate> matches() {
        return engine == null ? Collections.emptyList() :
            (usingNineKey() ? engine.lookupT9(composing) : engine.lookup(composing));
    }
    private void refreshCandidates() {
        candidatePage = 0;
        renderCandidates();
    }
    private void renderCandidates() {
        if (composingLabel == null) return;
        if (recognizer != null) {
            composingLabel.setText("语音 · " + voiceStatus);
            candidates.removeAllViews();
            Button cancel = key("取消语音", false);
            cancel.setOnClickListener(v -> cancelVoice());
            candidates.addView(cancel, new LinearLayout.LayoutParams(dp(100), -1));
            return;
        }
        composingLabel.setText(composing.isEmpty()
            ? (password ? "轻语 · 密码模式" : (!dictionaryAvailable ? "词库不可用 · 英文输入" : (chinese ? (usingNineKey() ? "轻语 · 九宫格" : "轻语 · 全键拼音") : "轻语 · English")))
            : composing + "  · 点击输入原文");
        candidates.removeAllViews();
        List<PinyinEngine.Candidate> list = matches();
        if (candidatePage > 0) candidateNavigation("‹ 上页", -1);
        int first = candidatePage * PAGE_SIZE;
        for (int i = first; i < Math.min(first + PAGE_SIZE, list.size()); i++) {
            PinyinEngine.Candidate c = list.get(i);
            Button b = key(usingNineKey() ? c.text + "\n" + c.pinyin : c.text, false);
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, usingNineKey() ? (sizing.compact ? 12 : 15) : (sizing.compact ? 16 : 19));
            b.setPadding(dp(12), 0, dp(12), 0);
            b.setContentDescription(c.text + "，拼音 " + c.pinyin);
            b.setOnClickListener(v -> select(c));
            candidates.addView(b, new LinearLayout.LayoutParams(-2, -1));
        }
        if (first + PAGE_SIZE < list.size()) candidateNavigation("下页 ›", 1);
        if (!composing.isEmpty() && list.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("暂无候选 · 可退格或点击上方输入原文");
            hint.setTextColor(Color.LTGRAY);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            candidates.addView(hint, new LinearLayout.LayoutParams(-2, -1));
        }
        if (composing.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(usingNineKey() ? "按 64426 → 你好 · 左右滑动候选" : "离线输入 · 长按空格切换输入法");
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizing.compact ? 11 : 13);
            hint.setTextColor(Color.rgb(148, 163, 184));
            hint.setGravity(Gravity.CENTER_VERTICAL);
            hint.setPadding(dp(12), 0, 0, 0);
            candidates.addView(hint, new LinearLayout.LayoutParams(-2, -1));
        }
        candidateScroll.scrollTo(0, 0);
    }
    private void candidateNavigation(String label, int delta) {
        Button b = key(label, true);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setOnClickListener(v -> { candidatePage += delta; renderCandidates(); });
        candidates.addView(b, new LinearLayout.LayoutParams(-2, -1));
    }
    private void typeT9(String digit) {
        cancelVoice(); selecting = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !usingNineKey()) return;
        if (composing.length() >= 64) commitRaw();
        composing += digit;
        ic.setComposingText(composing, 1);
        refreshCandidates();
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
        cancelVoice(); selecting = false;
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
        cancelVoice(); selecting = false;
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
        cancelVoice(); selecting = false;
        if (!composing.isEmpty()) {
            List<PinyinEngine.Candidate> list = matches();
            if (list.isEmpty()) commitRaw(); else select(list.get(0));
        } else type(" ");
    }
    private void enter() {
        cancelVoice(); selecting = false;
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

    private Button headerButton(String label, int width, Runnable action) {
        Button button = key(label, false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        button.setOnClickListener(v -> action.run());
        header.addView(button, new LinearLayout.LayoutParams(dp(width), -1));
        return button;
    }
    private void notifyUser(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    private void openPanel(String panelName) {
        cancelVoice(); commitRaw(); stopRepeat();
        toolPanel = toolPanel.equals(panelName) ? "" : panelName;
        drawKeys(); refreshCandidates();
    }
    private void returnToKeyboard() {
        toolPanel = ""; selecting = false;
        drawKeys(); refreshCandidates();
    }
    private void drawEditor() {
        LinearLayout arrows = row();
        add(arrows, "←", 1, false, () -> moveCursor(KeyEvent.KEYCODE_DPAD_LEFT)).setContentDescription("光标左移");
        add(arrows, "↑", 1, false, () -> moveCursor(KeyEvent.KEYCODE_DPAD_UP)).setContentDescription("光标上移");
        add(arrows, "↓", 1, false, () -> moveCursor(KeyEvent.KEYCODE_DPAD_DOWN)).setContentDescription("光标下移");
        add(arrows, "→", 1, false, () -> moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT)).setContentDescription("光标右移");
        LinearLayout selection = row();
        add(selection, selecting ? "选择中" : "选择", 1, selecting, () -> {
            selecting = !selecting;
            if (selecting) notifyUser("选择已开启，按方向键扩大或缩小选区");
            drawKeys();
        });
        add(selection, "全选", 1, false, () -> editorAction(android.R.id.selectAll));
        add(selection, "复制", 1, false, () -> editorAction(android.R.id.copy)).setEnabled(!password);
        LinearLayout actions = row();
        add(actions, "剪切", 1, false, () -> editorAction(android.R.id.cut)).setEnabled(!password);
        add(actions, "粘贴", 1, false, this::pasteClipboard);
        add(actions, "取消选择", 1, false, this::collapseSelection);
        LinearLayout last = row();
        add(last, "剪贴板", 1, false, () -> openPanel("clipboard"));
        add(last, "⌫", 1, false, this::backspace);
        add(last, "返回键盘", 1.5f, true, this::returnToKeyboard);
    }
    private void moveCursor(int code) {
        cancelVoice(); commitRaw();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int meta = selecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0;
        long now = SystemClock.uptimeMillis();
        int flags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        boolean handled = ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags));
        if (!handled) notifyUser("当前输入框不支持方向键移动");
    }
    private void collapseSelection() {
        selecting = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && selectionEnd >= 0) ic.setSelection(selectionEnd, selectionEnd);
        drawKeys();
    }
    private void editorAction(int action) {
        cancelVoice(); commitRaw();
        if (password && (action == android.R.id.copy || action == android.R.id.cut)) {
            notifyUser("密码框不提供复制或剪切"); return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if ((action == android.R.id.copy || action == android.R.id.cut)) {
            CharSequence selected = ic.getSelectedText(0);
            if (selected == null || selected.length() == 0) {
                notifyUser("请先选择文字"); return;
            }
        }
        if (!ic.performContextMenuAction(action)) notifyUser("当前输入框不支持此编辑操作");
        selecting = false;
        if (!toolPanel.isEmpty()) drawKeys();
    }
    private String readClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        try {
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            // Plain/HTML text only. Never dereference a clipboard URI or intent.
            CharSequence text = clip.getItemAt(0).getText();
            return text == null ? null : text.toString();
        } catch (SecurityException ex) { return null; }
    }
    private void pasteClipboard() {
        cancelVoice(); commitRaw();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // Fetch on explicit paste, so a changed system clipboard is not replaced by a stale preview.
        String text = readClipboardText();
        if (text == null || text.isEmpty()) { notifyUser("剪贴板没有可粘贴的文字"); return; }
        if (!ic.commitText(text, 1)) notifyUser("当前输入框无法粘贴");
        selecting = false;
        if (!toolPanel.isEmpty()) drawKeys();
    }
    private void drawClipboard() {
        ScrollView scroll = new ScrollView(this);
        TextView preview = new TextView(this);
        String text = password ? null : readClipboardText();
        preview.setText(password ? "密码框不显示剪贴板预览，可按粘贴输入。" :
            (text == null || text.isEmpty() ? "剪贴板暂无文字。复制文字后，点击刷新。" :
                text.substring(0, Math.min(text.length(), 4000)) + (text.length() > 4000 ? "\n…预览已截断，粘贴保留全文" : "")));
        preview.setTextColor(Color.rgb(226, 232, 240));
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizing.compact ? 14 : 17);
        preview.setPadding(dp(12), dp(6), dp(12), dp(6));
        scroll.addView(preview);
        rows.addView(scroll, new LinearLayout.LayoutParams(-1, dp(sizing.row * 2)));
        LinearLayout actions = row();
        add(actions, "粘贴", 1, true, this::pasteClipboard);
        add(actions, "刷新", 1, false, this::drawKeys);
        add(actions, "复制选中", 1.5f, false, () -> editorAction(android.R.id.copy)).setEnabled(!password);
        LinearLayout last = row();
        add(last, "光标编辑", 1, false, () -> openPanel("edit"));
        add(last, "返回键盘", 1, true, this::returnToKeyboard);
    }

    private void toggleVoice() {
        if (recognizer != null) {
            voiceStatus = "识别中…"; refreshCandidates();
            try { recognizer.stopListening(); }
            catch (RuntimeException ex) { cancelVoice(); notifyUser("语音服务不可用，请重试"); }
            return;
        }
        if (password) { notifyUser("密码框不启用语音"); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            notifyUser("未找到系统语音识别服务，请先安装或启用语音服务"); return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, VoicePermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        commitRaw(); selecting = false;
        InputConnection target = getCurrentInputConnection();
        if (target == null) return;
        final long session = voiceSession.start(target);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            else recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                private boolean current() { return voiceSession.accepts(session, getCurrentInputConnection()) && isInputViewShown(); }
                @Override public void onReadyForSpeech(Bundle params) {
                    if (current()) { voiceStatus = "请说话，点停止结束"; refreshCandidates(); }
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() {
                    if (current()) { voiceStatus = "识别中…"; refreshCandidates(); }
                }
                @Override public void onError(int error) {
                    if (!voiceSession.accepts(session, getCurrentInputConnection())) return;
                    cancelVoice(); notifyUser(speechError(error));
                }
                @Override public void onResults(Bundle results) {
                    if (!current()) return;
                    java.util.ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    cancelVoice();
                    if (texts == null || texts.isEmpty() || texts.get(0).trim().isEmpty()) {
                        notifyUser("未识别到文字，请重试"); return;
                    }
                    if (target == getCurrentInputConnection()) target.commitText(texts.get(0), 1);
                }
                @Override public void onPartialResults(Bundle partial) {
                    // Partial transcripts are never inserted or saved.
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            voiceStatus = "正在启动…";
            voiceButton.setText("停止"); refreshCandidates();
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, chinese ? "zh-CN" : "en-US");
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            handler.postDelayed(voiceTimeout, 30000);
            recognizer.startListening(intent);
        } catch (RuntimeException ex) {
            cancelVoice(); notifyUser("语音启动失败，请检查麦克风权限及系统语音服务");
        }
    }
    private void cancelVoice() {
        voiceSession.cancel();
        handler.removeCallbacks(voiceTimeout);
        SpeechRecognizer old = recognizer;
        recognizer = null;
        if (old != null) {
            try { old.cancel(); } catch (RuntimeException ignored) { }
            try { old.destroy(); } catch (RuntimeException ignored) { }
            voiceStatus = "";
            if (voiceButton != null) voiceButton.setText("语音");
            refreshCandidates();
        }
    }
    private String speechError(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "请允许麦克风权限后重试";
            case SpeechRecognizer.ERROR_NO_MATCH: return "没有识别到文字，请重试";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "未检测到说话声音";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "语音服务网络异常，请检查网络或离线语言包";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "语音服务正忙，请稍后再试";
            case SpeechRecognizer.ERROR_AUDIO: return "无法使用麦克风，请检查是否被其他应用占用";
            default: return "语音识别失败（" + code + "），请检查语音服务或语言包";
        }
    }

    private Button key(String text, boolean accent) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizing != null && sizing.compact ? 16 : 19);
        b.setSingleLine(true);
        b.setIncludeFontPadding(false);
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
        rows.addView(row, new LinearLayout.LayoutParams(-1, dp(sizing.row)));
        return row;
    }
    private Button add(LinearLayout row, String text, float weight, boolean accent, Runnable action) {
        Button b = key(text, accent);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, weight);
        p.setMargins(dp(2), dp(sizing.compact ? 2 : 3), dp(2), dp(sizing.compact ? 2 : 3)); row.addView(b, p);
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
        stopRepeat();
        sizing = windowSizing();
        header.getLayoutParams().height = dp(sizing.header);
        candidateScroll.getLayoutParams().height = dp(sizing.candidates);
        layoutToggle.setText(nineKey ? "切换 26 键" : "切换九宫格");
        layoutToggle.setVisibility(chinese && !password && !symbols ? View.VISIBLE : View.GONE);
        voiceButton.setEnabled(!password);
        rows.removeAllViews();
        if ("edit".equals(toolPanel)) { drawEditor(); return; }
        if ("clipboard".equals(toolPanel)) { drawClipboard(); return; }
        if (symbols) {
            letters("1234567890"); letters("@#￥%&*()-+"); letters("/：；！？.,=_");
        } else if (usingNineKey()) {
            String[] labels = {"1  ，。", "2  ABC", "3  DEF", "4  GHI", "5  JKL", "6  MNO", "7  PQRS", "8  TUV", "9  WXYZ"};
            for (int r = 0; r < 3; r++) {
                LinearLayout line = row();
                for (int col = 0; col < 3; col++) {
                    final int digit = r * 3 + col + 1;
                    Button b = add(line, labels[digit - 1], 1, false,
                        () -> { if (digit == 1) type("。"); else typeT9(String.valueOf(digit)); });
                    b.setContentDescription(digit == 1 ? "句号" : labels[digit - 1]);
                }
            }
        } else {
            letters("qwertyuiop"); letters("asdfghjkl"); letters("zxcvbnm");
        }
        LinearLayout last = row();
        add(last, symbols ? "ABC" : "123", 1.2f, false, () -> { cancelVoice(); commitRaw(); symbols = !symbols; drawKeys(); });
        add(last, chinese ? "中" : "EN", 1, false, () -> { cancelVoice(); commitRaw(); chinese = !chinese && !password; shifted = false; drawKeys(); refreshCandidates(); });
        if (!chinese && !symbols) add(last, shifted ? "⇧" : "↑", .8f, false, () -> { shifted = !shifted; drawKeys(); });
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
