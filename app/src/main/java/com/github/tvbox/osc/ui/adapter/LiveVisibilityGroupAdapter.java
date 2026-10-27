package com.github.tvbox.osc.ui.adapter;

import android.widget.CheckBox;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveVisibilityItem;

import java.util.ArrayList;

/**
 * 频道显示管理左栏: 分组行 (勾选=整组显示, 文本带频道数).
 * 交互由弹窗的 TvRecyclerView 统一处理: 焦点=预览频道, OK=整组翻转.
 */
public class LiveVisibilityGroupAdapter extends BaseQuickAdapter<LiveVisibilityItem, BaseViewHolder> {

    public LiveVisibilityGroupAdapter() {
        super(R.layout.item_live_visibility_group, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveVisibilityItem item) {
        CheckBox checkBox = holder.getView(R.id.cbGroupVisible);
        checkBox.setOnCheckedChangeListener(null);
        checkBox.setChecked(item.visible);
        checkBox.setText(item.groupName + (item.channelCount > 0 ? " (" + item.channelCount + ")" : ""));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 只回显数据状态; 翻转由弹窗在 onItemClick 里驱动
            if (isChecked != item.visible) checkBox.setChecked(item.visible);
        });
    }
}
