package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;
import java.io.File;

public class BackupAdapter extends BaseQuickAdapter<File, BaseViewHolder> {
    public BackupAdapter() {
        super(R.layout.item_dialog_backup, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        helper.setText(R.id.tvName, item.getName() + "\n" + item.getParent());
        helper.addOnClickListener(R.id.tvName);
        helper.addOnClickListener(R.id.tvDel);
    }
}
