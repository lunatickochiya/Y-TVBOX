package com.github.tvbox.osc.bean;

/**
 * 直播频道显示管理列表项: 分组或频道.
 */
public class LiveVisibilityItem {

    /** true=分组, false=频道 */
    public boolean isGroup;
    public String groupName;
    public String channelName;
    /** true=在频道列表显示 */
    public boolean visible;

    public static LiveVisibilityItem group(String groupName, boolean visible) {
        LiveVisibilityItem item = new LiveVisibilityItem();
        item.isGroup = true;
        item.groupName = groupName;
        item.visible = visible;
        return item;
    }

    public static LiveVisibilityItem channel(String groupName, String channelName, boolean visible) {
        LiveVisibilityItem item = new LiveVisibilityItem();
        item.isGroup = false;
        item.groupName = groupName;
        item.channelName = channelName;
        item.visible = visible;
        return item;
    }
}
