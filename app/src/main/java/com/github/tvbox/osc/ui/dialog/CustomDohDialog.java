package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

/**
 * 自定义安全DNS (DoH) 配置弹窗: 保存地址与可选的引导 IP。
 * 保存后回调 onChange, 由调用方决定选择与生效时机。
 */
public class CustomDohDialog extends BaseDialog {

    public CustomDohDialog(@NonNull @NotNull Context context) {
        super(context);
        setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_doh_custom);
        final EditText inputUrl = findViewById(R.id.inputDohUrl);
        final EditText inputIps = findViewById(R.id.inputDohIps);
        inputUrl.setText(Hawk.get(HawkConfig.DOH_CUSTOM_URL, ""));
        inputIps.setText(Hawk.get(HawkConfig.DOH_CUSTOM_IPS, ""));
        findViewById(R.id.inputCancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.inputSubmit).setOnClickListener(v -> {
            String url = inputUrl.getText().toString().trim();
            if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(context, "DNS 地址需以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show();
                return;
            }
            Hawk.put(HawkConfig.DOH_CUSTOM_URL, url);
            Hawk.put(HawkConfig.DOH_CUSTOM_IPS, inputIps.getText().toString().trim());
            if (listener != null) listener.onChange();
            dismiss();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        dismiss();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    OnListener listener = null;

    public interface OnListener {
        void onChange();
    }
}
