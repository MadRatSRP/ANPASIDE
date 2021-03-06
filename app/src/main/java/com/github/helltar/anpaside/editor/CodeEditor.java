package com.github.helltar.anpaside.editor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.github.helltar.anpaside.MainActivity;
import com.github.helltar.anpaside.R;
import com.github.helltar.anpaside.logging.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class CodeEditor {
    private final Context context;
    private final TabHost tabHost;
    public EditorConfig editorConfig;

    private String btnTabCloseName = "Close";
    private final int fontColor = Color.rgb(220, 220, 220);
    private final Typeface fontTypeface = Typeface.MONOSPACE;

    public static boolean isFilesModified = false;
    private final LinkedList<String> filenameList = new LinkedList<>();

    public CodeEditor(Context context, TabHost tabHost) {
        this.context = context;
        this.tabHost = tabHost;
        editorConfig = new EditorConfig(context);
    }

    public boolean openFile(String filename) {
        if (isFileOpen(filename)) {
            tabHost.setCurrentTabByTag(filename);
            return true;
        }

        String text;

        try {
            text = FileUtils.readFileToString(
                new File(filename),
                StandardCharsets.UTF_8
            );
        } catch (IOException ioe) {
            Logger.addLog(ioe);
            return false;
        }

        final EditText edtText = new CodeEditText(context);

        edtText.setTag(filename);

        edtText.setTextSize(editorConfig.getFontSize());
        edtText.setTextColor(fontColor);
        edtText.setTypeface(fontTypeface);

        edtText.setBackgroundColor(ContextCompat.getColor(
            context, R.color.TRANSPARENT)
        );
        edtText.setGravity(Gravity.TOP);
        edtText.setHorizontallyScrolling(true);
        edtText.addTextChangedListener(textWatcher);
        edtText.setOnKeyListener(keyListener);

        edtText.setText(text);

        filenameList.add(filename);
        isFilesModified = false;
        highlights(edtText.getEditableText());

        createTabs(filename, new File(filename).getName(), string -> {
            ScrollView sv = new ScrollView(context);
            sv.setFillViewport(true);
            sv.addView(edtText);
            return sv;
        });

        edtText.requestFocus();

        return true;
    }

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            MainActivity.svLog.setVisibility(View.GONE);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            isFilesModified = true;
        }

        @Override
        public void afterTextChanged(Editable s) {
            highlights(s);
        }
    };

    private final OnKeyListener keyListener = (view, keyCode, keyEvent) -> {
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            String tabIns = "    ";
            switch (keyCode) {
                case KeyEvent.KEYCODE_TAB:
                    EditText editText = (EditText) view;
                    editText.getText().insert(editText.getSelectionStart(), tabIns);
                    return true;

                case KeyEvent.KEYCODE_S:
                    if (keyEvent.isCtrlPressed()) {
                        saveAllFiles();
                        showToastFileSaved();
                        return true;
                    }

                    return false;
            }
        }

        return false;
    };

    private void highlights(Editable s) {
        if (editorConfig.getHighlighterEnabled()) {
            Highlighter.highlights(s);
        }
    }

    private void createTabs(String tag, String title, TabContentFactory tabContent) {
        final TabSpec tabSpec = tabHost.newTabSpec(tag);

        tabSpec.setIndicator(title);
        tabSpec.setContent(tabContent);

        tabHost.addTab(tabSpec);
        tabHost.setCurrentTabByTag(tag);

        tabHost.getTabWidget().getChildAt(tabHost.getTabWidget().getChildCount() - 1)
            .setOnLongClickListener(view -> {
                tabHost.setCurrentTabByTag(tabSpec.getTag());
                showPopupMenu(view, tabSpec.getTag());
                return true;
            });
    }

    private void showPopupMenu(View v, final String tag) {
        PopupMenu pm = new PopupMenu(context, v);

        pm.getMenu().add(btnTabCloseName);
        pm.setOnMenuItemClickListener(menuItem -> {
            closeFile(tag);
            return true;
        });

        pm.show();
    }

    public boolean saveAllFiles() {
        if (isEditorActive()) {
            try {
                for (int i = 0; i < filenameList.size(); i++) {
                    FileUtils.writeStringToFile(
                        new File(filenameList.get(i)),
                        getEditorWithTag(filenameList.get(i)).getText().toString(),
                        StandardCharsets.UTF_8
                    );
                }

                isFilesModified = false;
                return true;
            } catch (IOException ioe) {
                Logger.addLog(ioe);
            }
        }

        return false;
    }

    private EditText getEditorWithTag(String tag) {
        return (EditText) tabHost.getTabContentView().findViewWithTag(tag);
    }

    private boolean isEditorActive() {
        return filenameList.size() > 0;
    }

    private void closeFile(String filename) {
        // TODO: try del
        tabHost.getTabWidget().getChildAt(tabHost.getCurrentTab()).setVisibility(View.GONE);
        tabHost.getTabContentView().removeView(tabHost.getCurrentView());

        isFilesModified = false;
        filenameList.remove(filename);

        if (!filenameList.isEmpty()) {
            tabHost.setCurrentTabByTag(filenameList.getLast());
        }
    }

    private void showToastFileSaved() {
        Toast toast = Toast.makeText(context, context.getString(R.string.msg_saved),
            Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 80);
        toast.show();
    }

    private boolean isFileOpen(String filename) {
        return filenameList.contains(filename);
    }

    public void setBtnTabCloseName(String name) {
        btnTabCloseName = name;
    }

    public void setHighlighterEnabled(boolean he) {
        editorConfig.setHighlighterEnabled(he);

        if (!he) {
            for (int i = 0; i < filenameList.size(); i++) {
                Highlighter.clearSpans(getEditorWithTag(filenameList.get(i)).getEditableText());
            }
        }
    }

    public void setFontSize(int size) {
        editorConfig.setFontSize(size);

        for (int i = 0; i < filenameList.size(); i++) {
            getEditorWithTag(filenameList.get(i)).setTextSize(size);
        }
    }
}
