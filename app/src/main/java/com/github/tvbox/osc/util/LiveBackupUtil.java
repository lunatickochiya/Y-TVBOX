package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import com.github.tvbox.osc.base.App;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * y-live 设置备份 / 恢复.
 *
 * 备份目录与文件名和 Y-TVBOX 完全区分开, 互不干扰:
 *   y-live :  /sdcard/ylive_backup/ylive-backup-20261017-213000.json
 *   Y-TVBOX:  /sdcard/tvbox_backup/2026-10-17-213000/{hawk,sqlite}
 *
 * 备份内容: Hawk 的全部设置(直播源/EPG/历史记录/分组开关/解码设置/每频道记忆等)
 * 以及 Hawk 加密用的 cipher_key; 文件格式与 Y-TVBOX 的 hawk 文件一致,
 * 所以把 Y-TVBOX 备份里的 hawk 文件复制过来改名成 *.json 也能恢复.
 */
public class LiveBackupUtil {

    /** 备份目录名(与 Y-TVBOX 的 tvbox_backup 区分) */
    public static final String DIR_NAME = "ylive_backup";
    private static final String FILE_PREFIX = "ylive-backup-";
    private static final String FILE_SUFFIX = ".json";
    /** 最多保留多少个备份(超出自动删除最旧的) */
    private static final int KEEP_MAX = 10;

    private LiveBackupUtil() {
    }

    /**
     * 备份目录: 优先用 /sdcard/ylive_backup (可以和 Y-TVBOX 一样用文件管理器拷走),
     * 没有存储权限时退到应用私有外置目录(Android/data/包名/files/ylive_backup).
     */
    public static File backupDir() {
        File publicDir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        if (canWrite(publicDir)) {
            return publicDir;
        }
        File external = App.getInstance().getExternalFilesDir(null);
        if (external != null) {
            File privateDir = new File(external, DIR_NAME);
            if (canWrite(privateDir)) return privateDir;
        }
        return new File(App.getInstance().getFilesDir(), DIR_NAME);
    }

    private static boolean canWrite(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File probe = File.createTempFile(".probe-", ".tmp", dir);
            probe.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 立即备份, 返回备份文件; 失败返回 null */
    public static File backup() {
        try {
            File dir = backupDir();
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("app", "y-live");
            jsonObject.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            // Hawk 设置
            SharedPreferences hawk = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
            for (String key : hawk.getAll().keySet()) {
                jsonObject.put(key, hawk.getString(key, ""));
            }
            // Hawk 加密用的 key(没有加密时为空, 不影响)
            SharedPreferences crypto = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
            for (String key : crypto.getAll().keySet()) {
                jsonObject.put(key, crypto.getString(key, ""));
            }
            String name = FILE_PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + FILE_SUFFIX;
            File file = new File(dir, name);
            if (!FileUtils.writeSimple(jsonObject.toString().getBytes("UTF-8"), file)) {
                file.delete();
                return null;
            }
            cleanOld(dir);
            return file;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 备份文件列表(新的在前) */
    public static List<File> listBackups() {
        ArrayList<File> result = new ArrayList<>();
        for (File dir : backupDirs()) {
            try {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File file : files) {
                    if (isBackupFile(file) && file.canRead()) result.add(file);
                }
            } catch (SecurityException ignored) {
                // 公共目录没授权时仍列出私有目录备份。
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    private static List<File> backupDirs() {
        ArrayList<File> dirs = new ArrayList<>();
        dirs.add(new File(Environment.getExternalStorageDirectory(), DIR_NAME));
        File external = App.getInstance().getExternalFilesDir(null);
        if (external != null) dirs.add(new File(external, DIR_NAME));
        dirs.add(new File(App.getInstance().getFilesDir(), DIR_NAME));
        return dirs;
    }

    private static boolean isBackupFile(File file) {
        if (file == null || !file.isFile() || !file.getName().endsWith(FILE_SUFFIX)) return false;
        try {
            File parent = file.getCanonicalFile().getParentFile();
            for (File dir : backupDirs()) {
                if (dir.getCanonicalFile().equals(parent)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 恢复备份: 写回 Hawk 设置(调用方负责重启应用生效) */
    public static boolean restore(File backup) {
        if (!isBackupFile(backup)) return false;
        try {
            byte[] data = FileUtils.readSimple(backup);
            if (data == null) {
                return false;
            }
            JSONObject jsonObject = new JSONObject(new String(data, "UTF-8"));
            SharedPreferences hawk = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
            SharedPreferences crypto = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
            // 完整验证后再写入，坏文件不能只恢复一半设置。
            Iterator<String> validate = jsonObject.keys();
            while (validate.hasNext()) {
                String key = validate.next();
                if (!"app".equals(key) && !"time".equals(key)
                        && !(jsonObject.get(key) instanceof String)) return false;
            }
            SharedPreferences.Editor hawkEditor = hawk.edit();
            SharedPreferences.Editor cryptoEditor = crypto.edit();
            Iterator<String> it = jsonObject.keys();
            int count = 0;
            while (it.hasNext()) {
                String key = it.next();
                if ("app".equals(key) || "time".equals(key)) {
                    continue;
                }
                String value = jsonObject.getString(key);
                if ("cipher_key".equals(key)) {
                    cryptoEditor.putString(key, value);
                } else {
                    hawkEditor.putString(key, value);
                }
                count++;
            }
            return count > 0 && cryptoEditor.commit() && hawkEditor.commit();
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean delete(File file) {
        try {
            return isBackupFile(file) && file.delete();
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 只保留最近 KEEP_MAX 个备份 */
    private static void cleanOld(File dir) {
        try {
            File[] all = dir.listFiles(file -> file.isFile() && file.getName().startsWith(FILE_PREFIX)
                    && file.getName().endsWith(FILE_SUFFIX));
            if (all == null) return;
            Arrays.sort(all, (a, b) -> b.getName().compareTo(a.getName()));
            for (int i = KEEP_MAX; i < all.length; i++) {
                delete(all[i]);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
