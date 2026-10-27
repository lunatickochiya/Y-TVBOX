package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveVisibilityItem;
import com.github.tvbox.osc.ui.adapter.LiveVisibilityAdapter;
import com.github.tvbox.osc.ui.adapter.LiveVisibilityGroupAdapter;
import com.github.tvbox.osc.util.LiveChannelFilter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 频道显示管理对话框: 双栏结构, 左栏分组(整组勾选), 右栏当前分组的频道.
 * 取消勾选后不在频道列表显示.
 */
public class LiveChannelVisibilityDialog extends BaseDialog {

    public interface OnConfirmListener {
        void onConfirm(ArrayList<String> hidden);
    }

    private final List<LiveChannelGroup> groups;
    /** 全量扁平数据: 分组行 + 其下频道行, 勾选状态的唯一数据源 */
    private final List<LiveVisibilityItem> items = new ArrayList<>();
    private final LiveVisibilityGroupAdapter groupAdapter = new LiveVisibilityGroupAdapter();
    private final LiveVisibilityAdapter channelAdapter = new LiveVisibilityAdapter();
    private int selectedGroupPosition = 0;

    public LiveChannelVisibilityDialog(@NonNull @NotNull Context context, List<LiveChannelGroup> groups,
                                       final OnConfirmListener listener) {
        super(context);
        this.groups = groups;
        setContentView(R.layout.dialog_live_visibility);
        TextView title = findViewById(R.id.tvVisibilityTitle);
        if (title != null) title.setText("频道显示 (取消勾选隐藏)");
        List<String> hidden = LiveChannelFilter.getHidden();
        if (groups != null) {
            for (LiveChannelGroup group : groups) {
                if (group == null || group.getLiveChannels() == null) continue;
                LiveVisibilityItem groupItem = LiveVisibilityItem.group(group.getGroupName(),
                        !LiveChannelFilter.isGroupHidden(group.getGroupName(), hidden));
                groupItem.channelCount = group.getLiveChannels().size();
                items.add(groupItem);
                for (LiveChannelItem channel : group.getLiveChannels()) {
                    items.add(LiveVisibilityItem.channel(group.getGroupName(), channel.getChannelName(),
                            !LiveChannelFilter.isChannelHidden(group.getGroupName(), channel.getChannelName(), hidden)));
                }
            }
        }

        TvRecyclerView groupList = findViewById(R.id.listGroups);
        groupList.setHasFixedSize(true);
        groupList.setLayoutManager(new V7LinearLayoutManager(context, V7LinearLayoutManager.VERTICAL, false));
        groupAdapter.setNewData(groupRows());
        groupList.setAdapter(groupAdapter);
        groupList.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                // 焦点即选中: 右栏跟随显示该分组的频道
                if (position >= 0 && position != selectedGroupPosition) {
                    selectedGroupPosition = position;
                    refreshChannelPane();
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                LiveVisibilityItem item = groupRows().get(position);
                setVisible(item, !item.visible);
            }
        });

        TvRecyclerView channelList = findViewById(R.id.listChannels);
        channelList.setHasFixedSize(true);
        channelList.setLayoutManager(new V7LinearLayoutManager(context, V7LinearLayoutManager.VERTICAL, false));
        channelList.setAdapter(channelAdapter);
        channelList.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                List<LiveVisibilityItem> rows = channelRows();
                if (position < 0 || position >= rows.size()) return;
                LiveVisibilityItem item = rows.get(position);
                setVisible(item, !item.visible);
            }
        });

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
        refreshChannelPane();
    }

    /** 左栏分组行数据 (引用扁平列表里的分组项) */
    private List<LiveVisibilityItem> groupRows() {
        List<LiveVisibilityItem> rows = new ArrayList<>();
        for (LiveVisibilityItem item : items) {
            if (item.isGroup) rows.add(item);
        }
        return rows;
    }

    /** 右栏频道行数据: 当前选中分组的频道 (引用扁平列表里的频道项) */
    private List<LiveVisibilityItem> channelRows() {
        List<LiveVisibilityItem> rows = new ArrayList<>();
        List<LiveVisibilityItem> groups = groupRows();
        if (selectedGroupPosition < 0 || selectedGroupPosition >= groups.size()) return rows;
        String groupName = groups.get(selectedGroupPosition).groupName;
        for (LiveVisibilityItem item : items) {
            if (!item.isGroup && groupName.equals(item.groupName)) rows.add(item);
        }
        return rows;
    }

    /** 设置单项可见性并同步界面 (整组翻转时联动组内频道) */
    private void setVisible(LiveVisibilityItem item, boolean visible) {
        item.visible = visible;
        if (item.isGroup) {
            for (LiveVisibilityItem child : items) {
                if (!child.isGroup && item.groupName.equals(child.groupName)) child.visible = visible;
            }
        }
        groupAdapter.notifyDataSetChanged();
        refreshChannelPane();
    }

    private void setAllVisible(boolean visible) {
        for (LiveVisibilityItem item : items) {
            item.visible = visible;
        }
        groupAdapter.notifyDataSetChanged();
        refreshChannelPane();
    }

    /** 刷新右栏 + 空态提示 */
    private void refreshChannelPane() {
        List<LiveVisibilityItem> rows = channelRows();
        channelAdapter.setNewData(rows);
        TextView empty = findViewById(R.id.tvEmptyChannel);
        if (empty != null) empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
