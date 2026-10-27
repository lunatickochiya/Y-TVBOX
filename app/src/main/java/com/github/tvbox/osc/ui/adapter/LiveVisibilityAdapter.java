package com.github.tvbox.osc.ui.adapter;

import android.widget.CheckBox;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveVisibilityItem;

import java.util.ArrayList;

/**
 * 频道显示管理右栏: 当前分组的频道行 (勾选=显示).
 * 交互由弹窗的 TvRecyclerView 统一处理: OK/点击=翻转勾选.
 */
public class LiveVisibilityAdapter extends BaseQuickAdapter<LiveVisibilityItem, BaseViewHolder> {

    public LiveVisibilityAdapter() {
        super(R.layout.item_live_visibility, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveVisibilityItem item) {
        CheckBox checkBox = holder.getView(R.id.cbVisible);
        checkBox.setChecked(item.visible);
        TextView tvName = holder.getView(R.id.tvChannelName);
        tvName.setText(item.channelName);
        tvName.setTextColor(mContext.getResources().getColor(
                item.visible ? R.color.color_FFFFFF : R.color.color_FFFFFF_70));
    }
}
