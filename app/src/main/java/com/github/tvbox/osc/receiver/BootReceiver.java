package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 开机自启进直播
 * 在设置里开启"开机自启进直播"后, 盒子开机自动进入 TVBox,
 * 配合"启动时进直播"开关即可直接进直播界面, 变成纯电视播放器。
 *
 * 兼容两种广播:
 * - LOCKED_BOOT_COMPLETED: 设备未解锁时派发 (部分 Android 12+ 无锁屏 ROM 只发这个)
 * - BOOT_COMPLETED: 解锁后派发 (标准开机广播)
 * 需 directBootAware=true (manifest) 才能收到 LOCKED_BOOT_COMPLETED。
 *
 * ⚠️ Android 14: LOCKED_BOOT_COMPLETED 时 credential-encrypted 存储不可用,
 * Hawk/SharedPreferences 访问会抛异常。此时无法确认用户是否开启自启, 按默认
 * 关闭处理, 不自动拉起; 解锁后的 BOOT_COMPLETED 会按用户开关正常启动。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            try {
                final Context ctx = context.getApplicationContext();
                // 延迟启动: 等系统完全就绪 + 避免开机广播风暴期间被限制
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 默认关闭开机自启; 只有用户明确开启后才拉起
                            boolean start = false;
                            try {
                                start = Hawk.get(HawkConfig.BOOT_START_LIVE, false);
                            } catch (Throwable storageUnavailable) {
                                start = false;
                            }
                            if (start) {
                                Intent i = new Intent(ctx, HomeActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                ctx.startActivity(i);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }, 3000);
            } catch (Throwable ignored) {
            }
        }
    }
}
