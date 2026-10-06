package com.github.tvbox.osc.ui.adapter;

import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.LiveVisibilityItem;

import java.util.ArrayList;

/**
 * 频道显示管理列表: 分组/频道 + 勾选框(勾选=显示).
 */
public class LiveVisibilityAdapter extends BaseQuickAdapter<LiveVisibilityItem, BaseViewHolder> {

    public interface OnToggleListener {
        void onToggle(int position, boolean checked);
    }

    private OnToggleListener toggleListener;

    public LiveVisibilityAdapter() {
        super(R.layout.item_live_visibility, new ArrayList<>());
    }

    public void setOnToggleListener(OnToggleListener listener) {
        this.toggleListener = listener;
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveVisibilityItem item) {
        CheckBox checkBox = holder.getView(R.id.cbVisible);
        checkBox.setOnCheckedChangeListener(null);
        checkBox.setChecked(item.visible);
        if (item.isGroup) {
            checkBox.setText("▸ " + item.groupName);
            checkBox.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF));
        } else {
            checkBox.setText("      " + item.channelName);
            checkBox.setTextColor(mContext.getResources().getColor(R.color.color_FFFFFF_70));
        }
        final int position = holder.getAdapterPosition();
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (toggleListener != null && position >= 0) {
                    toggleListener.onToggle(position, isChecked);
                }
            }
        });
    }
}
