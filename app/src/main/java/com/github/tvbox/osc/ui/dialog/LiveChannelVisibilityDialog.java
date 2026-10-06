package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveVisibilityItem;
import com.github.tvbox.osc.ui.adapter.LiveVisibilityAdapter;
import com.github.tvbox.osc.util.LiveChannelFilter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 频道显示管理对话框: 分组/频道勾选(取消勾选后不在频道列表显示).
 */
public class LiveChannelVisibilityDialog extends BaseDialog {

    public interface OnConfirmListener {
        void onConfirm(ArrayList<String> hidden);
    }

    private final List<LiveVisibilityItem> items = new ArrayList<>();
    private LiveVisibilityAdapter adapter;

    public LiveChannelVisibilityDialog(@NonNull @NotNull Context context, List<LiveChannelGroup> groups,
                                       final OnConfirmListener listener) {
        super(context);
        setContentView(R.layout.dialog_live_visibility);
        List<String> hidden = LiveChannelFilter.getHidden();
        if (groups != null) {
            for (LiveChannelGroup group : groups) {
                if (group == null || group.getLiveChannels() == null) continue;
                items.add(LiveVisibilityItem.group(group.getGroupName(),
                        !LiveChannelFilter.isGroupHidden(group.getGroupName(), hidden)));
                for (LiveChannelItem channel : group.getLiveChannels()) {
                    items.add(LiveVisibilityItem.channel(group.getGroupName(), channel.getChannelName(),
                            !LiveChannelFilter.isChannelHidden(group.getGroupName(), channel.getChannelName(), hidden)));
                }
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
                toggleItem(position, checked);
            }
        });
        adapter.setNewData(items);
        findViewById(R.id.checkAll).setOnClickListener(v -> setAllVisible(true));
        findViewById(R.id.clearAll).setOnClickListener(v -> setAllVisible(false));
        findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            ArrayList<String> newHidden = new ArrayList<>();
            for (LiveVisibilityItem item : items) {
                if (!item.visible) {
                    newHidden.add(item.isGroup ? LiveChannelFilter.groupKey(item.groupName)
                            : LiveChannelFilter.channelKey(item.groupName, item.channelName));
                }
            }
            if (listener != null) {
                listener.onConfirm(newHidden);
            }
            dismiss();
        });
    }

    private void toggleItem(int position, boolean checked) {
        if (position < 0 || position >= items.size()) return;
        LiveVisibilityItem item = items.get(position);
        item.visible = checked;
        if (item.isGroup) {
            // 分组联动: 同组频道跟随分组勾选状态
            for (int i = position + 1; i < items.size(); i++) {
                LiveVisibilityItem child = items.get(i);
                if (child.isGroup) break;
                child.visible = checked;
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void setAllVisible(boolean visible) {
        for (LiveVisibilityItem item : items) {
            item.visible = visible;
        }
        adapter.notifyDataSetChanged();
    }
}
