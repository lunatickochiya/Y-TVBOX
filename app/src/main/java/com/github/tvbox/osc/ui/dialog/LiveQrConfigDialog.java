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
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.jetbrains.annotations.NotNull;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * y-live 扫码配置: 左边二维码(手机/电脑扫码打开本地网页, 在网页里填直播源/EPG源),
 * 右边也可以直接在电视上输入, 「历史」按钮选择/删除历史记录.
 */
public class LiveQrConfigDialog extends BaseDialog {

    public interface OnSaveListener {
        /**
         * @param liveUrl 直播源地址(未改变时为 null)
         * @param epgUrl  EPG 地址(未改变时为 null，空字符串表示停用)
         */
        void onSave(String liveUrl, String epgUrl);
    }

    private final ImageView ivQRCode;
    private final TextView tvAddress;
    private final EditText inputLive;
    private final EditText inputEpg;
    private final TextView tvStatus;

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
        tvStatus = findViewById(R.id.tvPushStatus);
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
            if (newLive.isEmpty()) {
                inputLive.setError("请输入直播源地址");
                inputLive.requestFocus();
                return;
            }
            Hawk.put(HawkConfig.LIVE_URL, newLive);
            Hawk.put(HawkConfig.EPG_URL, newEpg);
            // 只在地址变化时通知界面重载, 避免白刷一次频道列表
            if (listener != null) {
                listener.onSave(oldLive.equals(newLive) ? null : newLive, oldEpg.equals(newEpg) ? null : newEpg);
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
            tvAddress.setText("暂无可用局域网地址，请检查电视网络\n也可直接在下方输入直播源与 EPG");
            ivQRCode.setImageBitmap(null);
            return;
        }
        tvAddress.setText(String.format("手机/电脑扫码打开网页配置直播源与EPG源\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address,
                AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        tvStatus.setText("等待手机连接 · 手机与电视需在同一局域网");
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SUCCESS) refreshQRCode();
        else if (event.type == ServerEvent.SERVER_CONNECTION) tvStatus.setText("设备已连接，请在网页填写配置并保存");
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigReceived(RefreshEvent event) {
        String url = event.obj == null ? "" : event.obj.toString();
        if (event.type == RefreshEvent.TYPE_LIVE_URL_CHANGE) {
            inputLive.setText(url);
            tvStatus.setText("已收到直播源，正在加载频道…");
        } else if (event.type == RefreshEvent.TYPE_EPG_URL_CHANGE) {
            inputEpg.setText(url);
            tvStatus.setText(url.isEmpty() ? "已停用自定义节目单" : "已收到 EPG，正在更新节目单…");
        }
    }
}
