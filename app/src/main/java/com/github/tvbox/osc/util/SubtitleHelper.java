package com.github.tvbox.osc.util;

import android.content.Context;

import com.github.tvbox.osc.R;

/**
 * y-live 纯直播版: 只保留字幕颜色初始化(点播的字幕控件/大小/时延设置已移除).
 */
public class SubtitleHelper {
    private static int[][] subtitleTextColor = null;

    //初始化字幕颜色
    public static void initSubtitleColor(Context context) {
        int[] subtitleColor = context.getApplicationContext().getResources().getIntArray(R.array.subtitle_text_color);
        int[] shadowColor = context.getApplicationContext().getResources().getIntArray(R.array.subtitle_text_shadow_color);
        SubtitleHelper.subtitleTextColor = new int[][]{subtitleColor, shadowColor};
    }

    public static int[][] getSubtitleTextColor() {
        return subtitleTextColor;
    }
}
