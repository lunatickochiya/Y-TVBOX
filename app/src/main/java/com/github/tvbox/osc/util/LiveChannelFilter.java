package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播频道显示管理: 记录被隐藏的分组/频道, 并在加载频道列表时过滤.
 *
 * 存储键: "g|组名" 隐藏整个分组; "c|组名|频道名" 隐藏单个频道.
 */
public class LiveChannelFilter {

    public static final String GROUP_PREFIX = "g|";
    public static final String CHANNEL_PREFIX = "c|";

    private LiveChannelFilter() {
    }

    public static ArrayList<String> getHidden() {
        try {
            ArrayList<String> hidden = Hawk.get(HawkConfig.LIVE_HIDDEN, new ArrayList<String>());
            return hidden == null ? new ArrayList<String>() : hidden;
        } catch (Throwable e) {
            return new ArrayList<String>();
        }
    }

    public static void saveHidden(ArrayList<String> hidden) {
        Hawk.put(HawkConfig.LIVE_HIDDEN, hidden == null ? new ArrayList<String>() : hidden);
    }

    public static String groupKey(String groupName) {
        return GROUP_PREFIX + groupName;
    }

    public static String channelKey(String groupName, String channelName) {
        return CHANNEL_PREFIX + groupName + "|" + channelName;
    }

    public static boolean isGroupHidden(String groupName, List<String> hidden) {
        return groupName != null && hidden != null && hidden.contains(groupKey(groupName));
    }

    public static boolean isChannelHidden(String groupName, String channelName, List<String> hidden) {
        return channelName != null && hidden != null && hidden.contains(channelKey(groupName, channelName));
    }

    /**
     * 过滤被隐藏的分组/频道, 返回新的列表(不修改传入数据).
     * 注意: 过滤后需要重新编号, 因为直播界面用列表位置当索引.
     */
    public static List<LiveChannelGroup> filter(List<LiveChannelGroup> groups) {
        ArrayList<LiveChannelGroup> result = new ArrayList<>();
        if (groups == null) {
            return result;
        }
        List<String> hidden = getHidden();
        for (LiveChannelGroup group : groups) {
            if (group == null) continue;
            if (isGroupHidden(group.getGroupName(), hidden)) continue;
            ArrayList<LiveChannelItem> channels = new ArrayList<>();
            if (group.getLiveChannels() != null) {
                for (LiveChannelItem channel : group.getLiveChannels()) {
                    if (channel == null) continue;
                    if (isChannelHidden(group.getGroupName(), channel.getChannelName(), hidden)) continue;
                    channels.add(channel);
                }
            }
            if (channels.isEmpty()) continue;
            // 重新编号: 分组/频道索引必须与过滤后的列表位置一致
            for (int i = 0; i < channels.size(); i++) {
                channels.get(i).setChannelIndex(i);
            }
            LiveChannelGroup copy = new LiveChannelGroup();
            copy.setGroupIndex(result.size());
            copy.setGroupName(group.getGroupName());
            copy.setGroupPassword(group.getGroupPassword());
            copy.setLiveChannels(channels);
            result.add(copy);
        }
        return result;
    }
}
