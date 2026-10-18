package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** 直播播放器缓存与设置备份的文件操作。 */
public class FileUtils {
    public static boolean writeSimple(byte[] data, File dst) {
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(dst))) {
            output.write(data);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static byte[] readSimple(File src) {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(src));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void recursiveDelete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) recursiveDelete(child);
        file.delete();
    }

    public static String getExternalCachePath() {
        File external = App.getInstance().getExternalCacheDir();
        return (external == null ? App.getInstance().getCacheDir() : external).getAbsolutePath();
    }

    public static void cleanPlayerCache() {
        recursiveDelete(new File(getExternalCachePath(), "ijkcaches"));
    }
}
