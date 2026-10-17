package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.R;

/**
 * 直播地址 / EPG 地址编辑对话框(直播设置里使用).
 * 右侧「历史」按钮可查看/选择/删除历史记录.
 */
public class LiveUrlDialog extends BaseDialog {

    public interface OnSubmitListener {
        void onSubmit(String url);
    }

    public LiveUrlDialog(@NonNull Context context, String title, String hint, String current, final OnSubmitListener listener) {
        this(context, title, hint, current, null, listener);
    }

    /**
     * @param historyKey 历史记录 Hawk 键(如 HawkConfig.LIVE_HISTORY), null=不显示历史按钮
     */
    public LiveUrlDialog(@NonNull Context context, String title, String hint, String current,
                         @Nullable final String historyKey, final OnSubmitListener listener) {
        super(context);
        setContentView(R.layout.dialog_live_url);
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(title);
        final EditText etUrl = findViewById(R.id.etUrl);
        etUrl.setHint(hint);
        if (current != null && !current.isEmpty()) {
            etUrl.setText(current);
            etUrl.setSelection(current.length());
        }
        etUrl.requestFocus();
        View btnHistory = findViewById(R.id.btnHistory);
        if (historyKey == null) {
            btnHistory.setVisibility(View.GONE);
        } else {
            btnHistory.setOnClickListener(v ->
                    ApiHistoryDialog.showHistory(getContext(), historyKey, etUrl, title + "历史"));
        }
        findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.btnOk).setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (listener != null) {
                listener.onSubmit(url);
            }
            dismiss();
        });
        if (getWindow() != null) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }
}
