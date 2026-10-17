package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.BackupAdapter;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.LiveBackupUtil;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * y-live 备份 / 恢复设置对话框: 复用 Y-TVBOX 的备份界面布局,
 * 备份文件放在 /sdcard/ylive_backup/(与 Y-TVBOX 的 tvbox_backup 区分), 内容为 Hawk 全部设置.
 */
public class LiveBackupDialog extends BaseDialog {

    private BackupAdapter adapter;
    private TextView tvTip;

    public LiveBackupDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_backup);
        TextView title = findViewById(R.id.title);
        if (title != null) title.setText("备份 / 恢复设置");
        tvTip = findViewById(R.id.restore_tip);
        final TvRecyclerView tvRecyclerView = findViewById(R.id.list);
        adapter = new BackupAdapter();
        tvRecyclerView.setAdapter(adapter);
        refresh();

        adapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter baseAdapter, View view, int position) {
                final String name = (String) baseAdapter.getItem(position);
                if (view.getId() == R.id.tvName) {
                    restore(name);
                } else if (view.getId() == R.id.tvDel) {
                    LiveBackupUtil.delete(name);
                    refresh();
                }
            }
        });
        findViewById(R.id.backupNow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backup();
            }
        });
        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStoragePermission();
            }
        });
    }

    private void refresh() {
        List<String> backups = LiveBackupUtil.listBackups();
        adapter.setNewData(backups);
        String dir = LiveBackupUtil.backupDir().getAbsolutePath();
        if (backups.isEmpty()) {
            tvTip.setText("暂无备份\n备份目录: " + dir);
        } else {
            tvTip.setText("备份目录: " + dir);
        }
    }

    private void backup() {
        File file = LiveBackupUtil.backup();
        if (file != null) {
            Toast.makeText(getContext(), "备份成功: " + file.getName(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "备份失败, 请检查存储权限", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void restore(final String name) {
        if (!LiveBackupUtil.restore(name)) {
            Toast.makeText(getContext(), "恢复失败: " + name, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "恢复成功, 3 秒后自动重启应用", Toast.LENGTH_LONG).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                restartApp();
            }
        }, 3000);
    }

    private void restartApp() {
        try {
            Context context = getContext();
            if (context == null) return;
            Intent i = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(i);
                System.exit(0);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 申请存储权限: Android 11+ 自动跳"所有文件访问", Android 10 及以下用旧版读写权限;
     * 整体 try/catch, 避免个别盒子缺少系统设置页导致崩溃.
     * 没给权限也能用(备份会落到应用私有目录).
     */
    private void requestStoragePermission() {
        try {
            if (XXPermissions.isGranted(getContext(), DefaultConfig.StoragePermissionGroup())) {
                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                return;
            }
            XXPermissions.with(getContext())
                    .permission(DefaultConfig.StoragePermissionGroup())
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                refresh();
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                openPermissionSettings(permissions);
                            } else {
                                Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPermissionSettings(List<String> permissions) {
        try {
            XXPermissions.startPermissionActivity(getContext(), permissions);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
