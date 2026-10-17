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
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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
        File privateDir = new File(App.getInstance().getExternalFilesDir(null), DIR_NAME);
        return privateDir;
    }

    private static boolean canWrite(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File probe = new File(dir, ".probe");
            if (probe.createNewFile() || probe.exists()) {
                probe.delete();
                return true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
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
            jsonObject.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
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
            String name = FILE_PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + FILE_SUFFIX;
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
    public static List<String> listBackups() {
        ArrayList<String> result = new ArrayList<>();
        try {
            File dir = backupDir();
            File[] list = dir.listFiles();
            if (list == null) {
                return result;
            }
            Arrays.sort(list, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o2.getName().compareTo(o1.getName());
                }
            });
            for (File f : list) {
                if (f.isFile() && f.getName().endsWith(FILE_SUFFIX)) {
                    result.add(f.getName());
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    /** 恢复备份: 写回 Hawk 设置(调用方负责重启应用生效) */
    public static boolean restore(String fileName) {
        try {
            File backup = new File(backupDir(), fileName);
            byte[] data = FileUtils.readSimple(backup);
            if (data == null) {
                return false;
            }
            JSONObject jsonObject = new JSONObject(new String(data, "UTF-8"));
            SharedPreferences hawk = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
            SharedPreferences crypto = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
            Iterator<String> it = jsonObject.keys();
            int count = 0;
            while (it.hasNext()) {
                String key = it.next();
                if ("app".equals(key) || "time".equals(key)) {
                    continue;
                }
                String value = jsonObject.getString(key);
                if ("cipher_key".equals(key)) {
                    crypto.edit().putString(key, value).commit();
                } else {
                    hawk.edit().putString(key, value).commit();
                }
                count++;
            }
            return count > 0;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void delete(String fileName) {
        try {
            FileUtils.recursiveDelete(new File(backupDir(), fileName));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 只保留最近 KEEP_MAX 个备份 */
    private static void cleanOld(File dir) {
        try {
            List<String> all = listBackups();
            for (int i = KEEP_MAX; i < all.size(); i++) {
                delete(all.get(i));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
