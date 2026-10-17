package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ApiHistoryDialog extends BaseDialog {
    public ApiHistoryDialog(@NonNull @NotNull Context context) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_api_history);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    public void setAdapter(ApiHistoryDialogAdapter.SelectDialogInterface sourceBeanSelectDialogInterface, List<String> data, int select) {
        ApiHistoryDialogAdapter adapter = new ApiHistoryDialogAdapter(sourceBeanSelectDialogInterface);
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = ((TvRecyclerView) findViewById(R.id.list));
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(select);
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                tvRecyclerView.scrollToPosition(select);
            }
        });
    }

    /**
     * 历史记录对话框(直播地址/EPG地址共用): 点击条目回填输入框, 删除写回 Hawk.
     *
     * @param hawkKey Hawk 里保存历史记录的键(如 HawkConfig.LIVE_HISTORY)
     * @param input   点击后回填的输入框
     * @param tip     标题
     */
    public static void showHistory(Context context, final String hawkKey, final EditText input, String tip) {
        final ArrayList<String> history = Hawk.get(hawkKey, new ArrayList<String>());
        if (history == null || history.isEmpty()) {
            return;
        }
        String current = input.getText().toString().trim();
        int idx = history.indexOf(current);
        if (idx < 0) idx = 0;
        ApiHistoryDialog dialog = new ApiHistoryDialog(context);
        dialog.setTip(tip);
        dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
            @Override
            public void click(String value) {
                input.setText(value);
                input.setSelection(value.length());
                dialog.dismiss();
            }

            @Override
            public void del(String value, ArrayList<String> data) {
                Hawk.put(hawkKey, data);
            }
        }, history, idx);
        dialog.show();
    }
}
