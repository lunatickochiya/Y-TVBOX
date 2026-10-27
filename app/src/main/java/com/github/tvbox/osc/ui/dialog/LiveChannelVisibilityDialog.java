package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveVisibilityItem;
import com.github.tvbox.osc.ui.adapter.LiveVisibilityAdapter;
import com.github.tvbox.osc.ui.adapter.LiveVisibilityGroupAdapter;
import com.github.tvbox.osc.util.LiveChannelFilter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 频道显示管理对话框: 双栏结构, 左栏分组(整组勾选), 右栏当前分组的频道.
 * 取消勾选后不在频道列表显示.
 * 用普通 RecyclerView 而非 TvRecyclerView: 触摸/遥控的 item 点击都可靠.
 */
public class LiveChannelVisibilityDialog extends BaseDialog {

    public interface OnConfirmListener {
        void onConfirm(ArrayList<String> hidden);
    }

    private final List<LiveVisibilityItem> items = new ArrayList<>();
    private final LiveVisibilityGroupAdapter groupAdapter = new LiveVisibilityGroupAdapter();
    private final LiveVisibilityAdapter channelAdapter = new LiveVisibilityAdapter();
    private int selectedGroupPosition = 0;

    public LiveChannelVisibilityDialog(@NonNull @NotNull Context context, List<LiveChannelGroup> groups,
                                       final OnConfirmListener listener) {
        super(context);
        setContentView(R.layout.dialog_live_visibility);
        TextView title = findViewById(R.id.tvVisibilityTitle);
        if (title != null) title.setText("频道显示 (取消勾选隐藏)");
        // 主题色需用 Activity 上下文取, 弹窗自身的 Context 是主题包装类不能强转
        if (context instanceof com.github.tvbox.osc.base.BaseActivity) {
            groupAdapter.setSelectedColor(((com.github.tvbox.osc.base.BaseActivity) context).getThemeColor());
        }
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

        RecyclerView groupList = findViewById(R.id.listGroups);
        groupList.setHasFixedSize(true);
        groupList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        groupAdapter.setNewData(groupRows());
        groupList.setAdapter(groupAdapter);
        // 点击行=选中分组(右栏跟随); 点击勾选框=整组显示/隐藏
        groupAdapter.setOnItemClickListener((adapter, view, position) -> selectGroup(position));
        groupAdapter.setOnItemChildClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.cbGroupVisible) toggleGroup(position);
        });

        RecyclerView channelList = findViewById(R.id.listChannels);
        channelList.setHasFixedSize(true);
        channelList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        channelAdapter.setNewData(channelRows());
        channelList.setAdapter(channelAdapter);
        // 点击任意位置=翻转该频道勾选
        channelAdapter.setOnItemClickListener((adapter, view, position) -> {
            List<LiveVisibilityItem> rows = channelRows();
            if (position < 0 || position >= rows.size()) return;
            setVisible(rows.get(position), !rows.get(position).visible);
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

    private void selectGroup(int position) {
        if (position < 0) return;
        selectedGroupPosition = position;
        groupAdapter.setSelectedPosition(position);
        refreshChannelPane();
    }

    /** 整组翻转: 联动组内频道 */
    private void toggleGroup(int position) {
        List<LiveVisibilityItem> groups = groupRows();
        if (position < 0 || position >= groups.size()) return;
        selectGroup(position);
        LiveVisibilityItem groupItem = groups.get(position);
        setVisible(groupItem, !groupItem.visible);
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
        channelAdapter.notifyDataSetChanged();
    }

    private void setAllVisible(boolean visible) {
        for (LiveVisibilityItem item : items) {
            item.visible = visible;
        }
        groupAdapter.notifyDataSetChanged();
        channelAdapter.notifyDataSetChanged();
    }

    /** 刷新右栏 + 空态提示 */
    private void refreshChannelPane() {
        channelAdapter.setNewData(channelRows());
        TextView empty = findViewById(R.id.tvEmptyChannel);
        if (empty != null) empty.setVisibility(channelAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
