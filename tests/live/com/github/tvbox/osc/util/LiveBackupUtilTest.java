package com.github.tvbox.osc.util;

import android.os.Build;
import android.os.Environment;
import com.github.tvbox.osc.base.App;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class LiveBackupUtilTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("ylive-backup-test").toFile();
        try {
            App.internalFiles = new File(root, "internal");
            App.externalFiles = new File(root, "private");
            Environment.external = new File(root, "public");
            File oldDir = new File(App.externalFiles, "ylive_backup");
            oldDir.mkdirs();
            File oldBackup = new File(oldDir, "old.json");
            Files.write(oldBackup.toPath(), "{\"live_url\":\"saved\"}".getBytes(StandardCharsets.UTF_8));
            // Public storage is now writable, as after granting permission.
            List<?> backups = LiveBackupUtil.listBackups();
            boolean found = backups.stream().anyMatch(item -> "old.json".equals(
                    item instanceof File ? ((File) item).getName() : item.toString()));
            if (!found) throw new AssertionError("Granting storage access hides existing private backups");
            if (!LiveBackupUtil.restore(oldBackup)) throw new AssertionError("Private backup cannot be restored after grant");
            File publicDir = new File(Environment.external, "ylive_backup");
            publicDir.mkdirs();
            File sameName = new File(publicDir, "old.json");
            Files.write(sameName.toPath(), "{\"live_url\":\"other\"}".getBytes(StandardCharsets.UTF_8));
            if (LiveBackupUtil.listBackups().size() != 2)
                throw new AssertionError("Same-named backups in separate directories were collapsed");
            if (!LiveBackupUtil.restore(sameName) || !"other".equals(App.getInstance()
                    .getSharedPreferences("Hawk2", 0).getString("live_url", "")))
                throw new AssertionError("Restore selected a different backup than the clicked path");
            File broken = new File(publicDir, "invalid.json");
            Files.write(broken.toPath(), "{\"live_url\":\"broken\",\"bad\":{}}".getBytes(StandardCharsets.UTF_8));
            if (LiveBackupUtil.restore(broken) || !"other".equals(App.getInstance()
                    .getSharedPreferences("Hawk2", 0).getString("live_url", "")))
                throw new AssertionError("Invalid backup partially changed settings");
            File outside = new File(root, "outside.json");
            Files.write(outside.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
            LiveBackupUtil.delete(new File(oldDir, "../../outside.json"));
            if (!outside.isFile()) throw new AssertionError("Backup deletion escaped the backup directory");
            // A missing external volume must use an absolute internal directory.
            Environment.external = new File(root, "unavailable");
            Files.write(Environment.external.toPath(), new byte[]{1});
            App.externalFiles = null;
            File fallback = LiveBackupUtil.backupDir();
            if (!fallback.isAbsolute() || !fallback.equals(new File(App.internalFiles, "ylive_backup")))
                throw new AssertionError("Missing external storage did not use internal files: " + fallback);
            Build.VERSION.SDK_INT = 29;
            if (!Arrays.equals(DefaultConfig.StoragePermissionGroup(), new String[]{
                    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}))
                throw new AssertionError("Android 10 requests an unsupported special permission");
            Build.VERSION.SDK_INT = 30;
            if (!Arrays.equals(DefaultConfig.StoragePermissionGroup(), new String[]{
                    "android.permission.MANAGE_EXTERNAL_STORAGE"}))
                throw new AssertionError("Android 11 mixes special access with obsolete runtime permissions");
            System.out.println("PASS: backup discovery across permission changes, path bounds, internal fallback and versioned permissions");
        } finally { deleteTree(root); }
    }
    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
