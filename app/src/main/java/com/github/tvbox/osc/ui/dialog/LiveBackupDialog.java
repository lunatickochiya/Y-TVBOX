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
    private boolean permissionSettingsRequired;

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
                final File file = (File) baseAdapter.getItem(position);
                if (view.getId() == R.id.tvName) {
                    restore(file);
                } else if (view.getId() == R.id.tvDel) {
                    if (!LiveBackupUtil.delete(file)) {
                        Toast.makeText(getContext(), "删除失败，请检查文件读写权限", Toast.LENGTH_SHORT).show();
                    }
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
        List<File> backups = LiveBackupUtil.listBackups();
        adapter.setNewData(backups);
        String dir = LiveBackupUtil.backupDir().getAbsolutePath();
        String access = XXPermissions.isGranted(getContext(), DefaultConfig.StoragePermissionGroup())
                ? "已获得共享存储读写权限" : "未授权共享存储，仍可备份到应用私有目录";
        tvTip.setText(access + "\n新备份保存到：" + dir
                + (backups.isEmpty() ? "\n暂无备份" : "\n选择下方文件恢复设置"));
    }

    private void backup() {
        File file = LiveBackupUtil.backup();
        if (file != null) {
            Toast.makeText(getContext(), "备份成功: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "备份失败, 请检查存储权限", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void restore(final File file) {
        if (!LiveBackupUtil.restore(file)) {
            Toast.makeText(getContext(), "恢复失败: " + file.getName(), Toast.LENGTH_SHORT).show();
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
            if (permissionSettingsRequired) {
                openPermissionSettings(java.util.Arrays.asList(DefaultConfig.StoragePermissionGroup()));
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
                            permissionSettingsRequired = never;
                            if (never) {
                                Toast.makeText(getContext(), "共享存储未授权，可再次点击按钮前往设置；私有备份仍可使用", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "共享存储未授权，私有备份仍可使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "无法打开授权页，请在系统应用权限中开启；私有备份仍可使用", Toast.LENGTH_LONG).show();
            openPermissionSettings(java.util.Arrays.asList(DefaultConfig.StoragePermissionGroup()));
        }
    }

    private void openPermissionSettings(List<String> permissions) {
        try {
            XXPermissions.startPermissionActivity(getContext(), permissions);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && adapter != null) refresh();
    }
}
