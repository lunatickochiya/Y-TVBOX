package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveVisibilityItem;
import com.github.tvbox.osc.ui.adapter.LiveVisibilityAdapter;
import com.github.tvbox.osc.util.LiveChannelFilter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播分组开关: 只列出分组(勾选=开启, 取消勾选=关闭整个分组),
 * 用于一键关掉不需要的分组, 例如关闭 4K 分组.
 * 只更新分组级隐藏项, 保留「频道显示」里单独隐藏的频道设置.
 */
public class LiveGroupToggleDialog extends BaseDialog {

    public interface OnConfirmListener {
        void onConfirm(ArrayList<String> hidden);
    }

    private final List<LiveVisibilityItem> items = new ArrayList<>();
    private LiveVisibilityAdapter adapter;

    public LiveGroupToggleDialog(@NonNull @NotNull Context context, List<LiveChannelGroup> groups,
                                 final OnConfirmListener listener) {
        super(context);
        setContentView(R.layout.dialog_live_visibility);
        TextView title = findViewById(R.id.tvVisibilityTitle);
        if (title != null) title.setText("分组开关 (取消勾选关闭分组)");

        List<String> hiddenGroups = LiveChannelFilter.getHiddenGroups();
        if (groups != null) {
            for (LiveChannelGroup group : groups) {
                if (group == null || group.getLiveChannels() == null || group.getLiveChannels().isEmpty()) continue;
                LiveVisibilityItem item = LiveVisibilityItem.group(group.getGroupName(),
                        !hiddenGroups.contains(group.getGroupName()));
                item.channelCount = group.getLiveChannels().size();
                items.add(item);
            }
        }

        TvRecyclerView list = findViewById(R.id.list);
        list.setHasFixedSize(true);
        list.setLayoutManager(new V7LinearLayoutManager(context, V7LinearLayoutManager.VERTICAL, false));
        adapter = new LiveVisibilityAdapter();
        list.setAdapter(adapter);
        adapter.setOnToggleListener(new LiveVisibilityAdapter.OnToggleListener() {
            @Override
            public void onToggle(int position, boolean checked) {
                if (position >= 0 && position < items.size()) {
                    items.get(position).visible = checked;
                }
            }
        });
        adapter.setNewData(items);
        findViewById(R.id.checkAll).setOnClickListener(v -> setAllVisible(true));
        findViewById(R.id.clearAll).setOnClickListener(v -> setAllVisible(false));
        findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            ArrayList<String> allGroups = new ArrayList<>();
            ArrayList<String> hiddenGroupNames = new ArrayList<>();
            for (LiveVisibilityItem item : items) {
                allGroups.add(item.groupName);
                if (!item.visible) hiddenGroupNames.add(item.groupName);
            }
            if (listener != null) {
                listener.onConfirm(LiveChannelFilter.updateHiddenGroups(allGroups, hiddenGroupNames));
            }
            dismiss();
        });
    }

    private void setAllVisible(boolean visible) {
        for (LiveVisibilityItem item : items) {
            item.visible = visible;
        }
        adapter.notifyDataSetChanged();
    }
}
