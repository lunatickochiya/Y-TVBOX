package com.github.tvbox.osc.bean;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Doh {

    private String name;
    private String url;
    private List<String> ips;

    public static List<Doh> get(Context context) {
        List<Doh> items = new ArrayList<>();
        String[] urls = context.getResources().getStringArray(R.array.doh_url);
        String[] names = context.getResources().getStringArray(R.array.doh_name);
        for (int i = 0; i < names.length; i++) items.add(new Doh().name(names[i]).url(urls[i]));
        return items;
    }

    public static List<Doh> arrayFrom(String json) {
        // 不走 Gson 反射: R8 水平类合并可能把 Doh 并进抽象类,
        // Gson 反射实例化会抛 "Abstract classes can't be instantiated"
        List<Doh> items = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return items;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                Doh item = new Doh().name(obj.optString("name")).url(obj.optString("url"));
                JSONArray ips = obj.optJSONArray("ips");
                if (ips != null) {
                    List<String> ipList = new ArrayList<>();
                    for (int j = 0; j < ips.length(); j++) {
                        String ip = ips.optString(j);
                        if (!TextUtils.isEmpty(ip)) ipList.add(ip);
                    }
                    item.ips(ipList);
                }
                items.add(item);
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public Doh name(String name) {
        this.name = name;
        return this;
    }

    public Doh url(String url) {
        this.url = url;
        return this;
    }

    public Doh ips(List<String> ips) {
        this.ips = ips;
        return this;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public List<String> getIps() {
        return ips == null ? Collections.emptyList() : ips;
    }

    public List<InetAddress> getHosts() {
        try {
            List<InetAddress> list = new ArrayList<>();
            for (String ip : getIps()) list.add(InetAddress.getByName(ip));
            return list.isEmpty() ? null : list;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Doh)) return false;
        Doh it = (Doh) obj;
        return getUrl().equals(it.getUrl());
    }

    @NonNull
    @Override
    public String toString() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", getName());
            obj.put("url", getUrl());
            JSONArray ips = new JSONArray();
            for (String ip : getIps()) ips.put(ip);
            obj.put("ips", ips);
        } catch (Exception ignored) {
        }
        return obj.toString();
    }
}
