package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 开机自启进直播 (y-live: 应用启动即直播界面, 这里直接拉起直播)
 * 在设置里开启"开机自启进直播"后, 盒子开机自动进入 y-live 直播界面,
 * 变成纯电视播放器。
 *
 * 兼容两种广播:
 * - LOCKED_BOOT_COMPLETED: 设备未解锁时派发 (部分 Android 12+ 无锁屏 ROM 只发这个)
 * - BOOT_COMPLETED: 解锁后派发 (标准开机广播)
 * 需 directBootAware=true (manifest) 才能收到 LOCKED_BOOT_COMPLETED。
 *
 * ⚠️ 关键坑 (Android 14): LOCKED_BOOT_COMPLETED 时 credential-encrypted 存储
 * 不可用, Hawk/SharedPreferences 访问会抛 IllegalStateException 崩溃!
 * 因此: 未解锁时不能读 Hawk 开关 → 用"无条件启动"策略 (用户既然装了 TVBox
 * 并开了自启, 开机拉起 HomeActivity 是合理行为, HomeActivity 内部会正常加载)。
 * 若确实想尊重开关, 只能把开关存到 DeviceProtectedStorage (较复杂, 暂不用)。
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
                            // 尝试读开关; 若存储不可用(未解锁)则默认启动
                            boolean start = true;
                            try {
                                start = Hawk.get(HawkConfig.BOOT_START_LIVE, true);
                            } catch (Throwable storageUnavailable) {
                                start = true;
                            }
                            if (start) {
                                Intent i = new Intent(ctx, LivePlayActivity.class);
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
