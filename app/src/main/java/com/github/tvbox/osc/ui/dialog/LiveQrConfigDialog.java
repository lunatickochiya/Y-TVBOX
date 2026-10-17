package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * y-live 扫码配置: 左边二维码(手机/电脑扫码打开本地网页, 在网页里填直播源/EPG源),
 * 右边也可以直接在电视上输入, 「历史」按钮选择/删除历史记录.
 */
public class LiveQrConfigDialog extends BaseDialog {

    public interface OnSaveListener {
        /**
         * @param liveUrl 直播源地址(未改变时为空字符串)
         * @param epgUrl  EPG 地址(未改变时为空字符串)
         */
        void onSave(String liveUrl, String epgUrl);
    }

    private final ImageView ivQRCode;
    private final TextView tvAddress;
    private final EditText inputLive;
    private final EditText inputEpg;

    public LiveQrConfigDialog(@NonNull @NotNull Context context) {
        this(context, null);
    }

    public LiveQrConfigDialog(@NonNull @NotNull Context context, final OnSaveListener listener) {
        super(context);
        setContentView(R.layout.dialog_live_qr_config);
        setCanceledOnTouchOutside(true);
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputLive = findViewById(R.id.input_live);
        inputEpg = findViewById(R.id.input_epg);
        inputLive.setText(Hawk.get(HawkConfig.LIVE_URL, ""));
        inputEpg.setText(Hawk.get(HawkConfig.EPG_URL, ""));

        // 历史记录: 选择(填入输入框)或删除
        findViewById(R.id.liveHistory).setOnClickListener(v ->
                ApiHistoryDialog.showHistory(getContext(), HawkConfig.LIVE_HISTORY, inputLive, "直播源历史"));
        findViewById(R.id.EPGHistory).setOnClickListener(v ->
                ApiHistoryDialog.showHistory(getContext(), HawkConfig.EPG_HISTORY, inputEpg, "EPG源历史"));

        findViewById(R.id.inputSubmit).setOnClickListener(v -> {
            String oldLive = Hawk.get(HawkConfig.LIVE_URL, "");
            String oldEpg = Hawk.get(HawkConfig.EPG_URL, "");
            String newLive = inputLive.getText().toString().trim();
            String newEpg = inputEpg.getText().toString().trim();
            Hawk.put(HawkConfig.LIVE_URL, newLive);
            Hawk.put(HawkConfig.EPG_URL, newEpg);
            // 只在地址变化时通知界面重载, 避免白刷一次频道列表
            if (listener != null) {
                listener.onSave(oldLive.equals(newLive) ? "" : newLive, oldEpg.equals(newEpg) ? "" : newEpg);
            }
            dismiss();
        });
        refreshQRCode();
    }

    private void refreshQRCode() {
        String address = "";
        try {
            address = ControlManager.get().getAddress(false);
        } catch (Throwable ignored) {
            // 服务器还没起来时不显示地址, 也不影响电视上直接输入
        }
        if (address == null || address.isEmpty()) {
            tvAddress.setText("手机/电脑扫码打开网页配置直播源与EPG源\n(本地服务未启动, 请直接在下方输入)");
            ivQRCode.setImageBitmap(null);
            return;
        }
        tvAddress.setText(String.format("手机/电脑扫码打开网页配置直播源与EPG源\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address,
                AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }
}
