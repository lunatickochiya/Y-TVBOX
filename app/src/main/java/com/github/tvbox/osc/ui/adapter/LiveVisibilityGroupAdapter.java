package com.github.tvbox.osc.ui.adapter;

import android.widget.CheckBox;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveVisibilityItem;

import java.util.ArrayList;

/**
 * 频道显示管理左栏: 分组行 (勾选=整组显示).
 * 交互由弹窗的 TvRecyclerView 统一处理: 焦点=选中分组预览频道, OK/点击=整组翻转.
 */
public class LiveVisibilityGroupAdapter extends BaseQuickAdapter<LiveVisibilityItem, BaseViewHolder> {

    private int selectedPosition = 0;
    /** 选中分组标题的主题色, 由弹窗用 Activity 上下文取好后传入 */
    private int selectedColor = -1;

    public LiveVisibilityGroupAdapter() {
        super(R.layout.item_live_visibility_group, new ArrayList<>());
    }

    public void setSelectedColor(int color) {
        selectedColor = color;
    }

    public void setSelectedPosition(int position) {
        if (position == selectedPosition) return;
        int pre = selectedPosition;
        selectedPosition = position;
        if (pre >= 0 && pre < getItemCount()) notifyItemChanged(pre);
        if (position >= 0 && position < getItemCount()) notifyItemChanged(position);
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveVisibilityItem item) {
        CheckBox checkBox = holder.getView(R.id.cbGroupVisible);
        checkBox.setChecked(item.visible);
        TextView tvName = holder.getView(R.id.tvGroupName);
        tvName.setText(item.groupName);
        TextView tvCount = holder.getView(R.id.tvGroupCount);
        tvCount.setText(item.channelCount > 0 ? String.valueOf(item.channelCount) : "");
        // 当前选中的分组用主题色标题, 其余白色
        int color = holder.getAdapterPosition() == selectedPosition && selectedColor != -1
                ? selectedColor
                : mContext.getResources().getColor(R.color.color_FFFFFF);
        tvName.setTextColor(color);
        holder.addOnClickListener(R.id.cbGroupVisible);
    }
}
