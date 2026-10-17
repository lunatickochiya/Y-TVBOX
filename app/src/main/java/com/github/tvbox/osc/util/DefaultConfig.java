package com.github.tvbox.osc.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hjq.permissions.Permission;

import java.util.ArrayList;

/**
 * y-live 纯直播版: 只保留直播需要的通用工具(存储权限组 / 安全的 JSON 取值)
 */
public class DefaultConfig {

    public static ArrayList<String> safeJsonStringList(JsonObject obj, String key) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (obj.has(key)) {
                if (obj.get(key).isJsonObject()) {
                    result.add(obj.get(key).getAsString());
                } else {
                    for (JsonElement opt : obj.getAsJsonArray(key)) {
                        result.add(opt.getAsString());
                    }
                }
            }
        } catch (Throwable th) {
        }
        return result;
    }

    public static String[] StoragePermissionGroup() {
        return new String[]{Permission.MANAGE_EXTERNAL_STORAGE, Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE};
    }
}
