package com.github.tvbox.osc.base;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.core.os.HandlerCompat;
import androidx.multidex.MultiDexApplication;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LocaleHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.hjq.permissions.XXPermissions;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;

import java.io.File;

import io.github.inflationx.calligraphy3.CalligraphyConfig;
import io.github.inflationx.calligraphy3.CalligraphyInterceptor;
import io.github.inflationx.viewpump.ViewPump;
import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;
    public static ViewPump viewPump = null;
    private final Handler handler;

    public App() {
        instance = this;
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SubtitleHelper.initSubtitleColor(this);
        initParams();
        // takagen99 : Initialize Locale
        initLocale();
        // OKGo
        OkGoHelper.init();
        // 闭关检查模式
        XXPermissions.setCheckMode(false);
        // Get EPG Info
        EpgUtil.init();
        // 初始化Web服务器(直播列表代理 + 手机扫码推送必须)
        ControlManager.init(this);
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        // 修复 Android 14+：冷启动时 ScreenUtils 可能获取到竖屏宽度，导致 xdpi 计算错误、UI 变小
        int screenWidth = AutoSizeConfig.getInstance().getScreenWidth();
        int screenHeight = AutoSizeConfig.getInstance().getScreenHeight();
        if (screenWidth < screenHeight) {
            AutoSizeConfig.getInstance().setScreenWidth(screenHeight);
            AutoSizeConfig.getInstance().setScreenHeight(screenWidth);
        }
        // y-live: 不再预热 IJK 原生库(PlayerHelper.init 里的 loadLibrariesOnce),
        // 只有选 ijk 硬解/软解时才加载, 启动更快、Exo/系统播放时不占原生内存

        // y-live: 直播界面只加载频道台标等小图, 把 Glide 图片内存缓存/位图池调低一档, 降低常驻内存
        Glide.get(this).setMemoryCategory(MemoryCategory.LOW);

        // Delete Cache
        /*File dir = getCacheDir();
        FileUtils.recursiveDelete(dir);
        dir = getExternalCacheDir();
        FileUtils.recursiveDelete(dir);*/

        FileUtils.cleanPlayerCache();

        // y-live: QuickJS 只有点播爬虫用到, 纯直播版不初始化(省内存 + 加快启动)

        // add font support, my tv embed font not include emoji
        String extStorageDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        File fontFile = new File(extStorageDir + "/tvbox.ttf");
        if (fontFile.exists()) {
            viewPump = ViewPump.builder()
                    .addInterceptor(new CalligraphyInterceptor(
                            new CalligraphyConfig.Builder()
                                    .setDefaultFontPath(fontFile.getAbsolutePath())
                                    .setFontAttrId(R.attr.fontPath)
                                    .build()))
                    .build();
        }
    }


    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);

        // 播放器: 0=系统, 1=IJK, 2=Exo (纯直播版默认 Exo)
        putDefault(HawkConfig.PLAY_TYPE, 2);
        // IJK 解码: 软解码 / 硬解码
        putDefault(HawkConfig.IJK_CODEC, "软解码");
        // 直播默认播放器: 0=系统, 1=IJK, 2=Exo(配合下面 Exo 默认软解, 菜单里即「exo软解」)
        putDefault(HawkConfig.LIVE_PLAYER_TYPE, 2);
        // Exo 解码方式: 0=硬解, 1=软解(纯直播版默认软解)
        putDefault(HawkConfig.LIVE_EXO_DECODE, 1);
        // 画面缩放: 0=默认, 1=16:9, 2=4:3, 3=填充, 4=原始, 5=裁剪
        putDefault(HawkConfig.PLAY_SCALE, 0);
        // 后台播放: 0=关闭, 1=开启, 2=画中画
        putDefault(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
        // 语言: 0=中文, 1=英文
        putDefault(HawkConfig.HOME_LOCALE, 0);
    }

    private void initLocale() {
        if (Hawk.get(HawkConfig.HOME_LOCALE, 0) == 0) {
            LocaleHelper.setLocale(App.this, "zh");
        } else {
            LocaleHelper.setLocale(App.this, "");
        }
    }

    public static App getInstance() {
        return instance;
    }

    private void putDefault(String key, Object value) {
        if (!Hawk.contains(key)) {
            Hawk.put(key, value);
        }
    }

    public static void post(Runnable runnable) {
        getInstance().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        getInstance().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) getInstance().handler.postDelayed(runnable, delayMillis);
    }
}
