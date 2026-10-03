package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Epginfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Pure-Java XMLTV parser and channel matcher for TVBox EPG.
 *
 * <p>The router-side {@code 2wan-route-iptv} generates XMLTV
 * ({@code /www/iptv_epg.xml[.gz]}) instead of the DIYP {@code epg_data} JSON,
 * so the app needs to understand {@code <channel>} / {@code <programme>} to
 * show the operator EPG.
 *
 * <p>Channels are matched by normalized channel id / display-name against the
 * requested tag name (from the bundled epg_data.json) and the channel name.
 * Normalization strips whitespace and punctuation, so {@code CCTV-4K},
 * {@code CCTV4K} and {@code cctv_4k} are all equivalent.
 */
public final class XmltvEpgParser {

    private static final TimeZone CST = TimeZone.getTimeZone("GMT+8:00");
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private XmltvEpgParser() {
    }

    public static final class Programme {
        public long start;
        public long stop;
        public String title;
    }

    public static final class Parsed {
        /** normalized id/display-name -> channel id */
        final Map<String, String> keyToId;
        /** channel id -> programmes sorted by start time */
        final Map<String, List<Programme>> programmes;

        Parsed(Map<String, String> keyToId, Map<String, List<Programme>> programmes) {
            this.keyToId = keyToId;
            this.programmes = programmes;
        }
    }

    public static Parsed parse(String xml) {
        Map<String, String> keyToId = new HashMap<>();
        Map<String, List<Programme>> programmes = new HashMap<>();
        if (xml == null) return new Parsed(keyToId, programmes);

        // <channel id="..."><display-name>...</display-name>...</channel>
        int pos = 0;
        while ((pos = xml.indexOf("<channel", pos)) >= 0) {
            int tagEnd = xml.indexOf('>', pos);
            if (tagEnd < 0) break;
            if (tagEnd > pos && xml.charAt(tagEnd - 1) == '/') {
                pos = tagEnd + 1;
                continue;
            }
            String attrs = xml.substring(pos, tagEnd);
            String id = attr(attrs, "id");
            int close = xml.indexOf("</channel>", tagEnd);
            if (close < 0) close = xml.length();
            String body = xml.substring(tagEnd + 1, close);
            if (id != null) {
                registerKey(keyToId, id, id);
                int p = 0;
                while ((p = body.indexOf("<display-name", p)) >= 0) {
                    int e = body.indexOf('>', p);
                    int ec = e >= 0 ? body.indexOf("</display-name>", e) : -1;
                    if (e < 0 || ec < 0) break;
                    String name = unescape(body.substring(e + 1, ec)).trim();
                    if (!name.isEmpty()) registerKey(keyToId, name, id);
                    p = ec + 1;
                }
            }
            pos = close + 1;
        }

        // <programme start="yyyyMMddHHmmss +0800" stop="..." channel="..."><title>...</title></programme>
        pos = 0;
        while ((pos = xml.indexOf("<programme", pos)) >= 0) {
            int tagEnd = xml.indexOf('>', pos);
            if (tagEnd < 0) break;
            int close = xml.indexOf("</programme>", tagEnd);
            if (close < 0) break;
            String attrs = xml.substring(pos, tagEnd);
            String channel = attr(attrs, "channel");
            long start = parseTime(attr(attrs, "start"));
            long stop = parseTime(attr(attrs, "stop"));
            String title = tagText(xml.substring(tagEnd + 1, close), "title");
            if (channel != null && start > 0 && stop > start && title != null && !title.isEmpty()) {
                Programme programme = new Programme();
                programme.start = start;
                programme.stop = stop;
                programme.title = title;
                List<Programme> list = programmes.get(channel);
                if (list == null) {
                    list = new ArrayList<>();
                    programmes.put(channel, list);
                }
                list.add(programme);
            }
            pos = close + 1;
        }

        for (List<Programme> list : programmes.values()) {
            Collections.sort(list, new Comparator<Programme>() {
                @Override
                public int compare(Programme a, Programme b) {
                    if (a.start != b.start) return a.start < b.start ? -1 : 1;
                    return a.title.compareTo(b.title);
                }
            });
        }
        return new Parsed(keyToId, programmes);
    }

    /**
     * Filter programmes of the requested day for one channel.
     *
     * @param tagName  epg id from the bundled epg_data.json, may be null
     * @param date     requested day (interpreted in GMT+8)
     */
    public static List<Epginfo> query(Parsed parsed, String channelName, String tagName, Date date) {
        List<Epginfo> result = new ArrayList<>();
        if (parsed == null) return result;

        Set<String> ids = new HashSet<>();
        addChannelId(parsed, ids, tagName);
        addChannelId(parsed, ids, channelName);
        if (ids.isEmpty()) return result;

        Calendar calendar = Calendar.getInstance(CST);
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long dayStart = calendar.getTimeInMillis();
        long dayEnd = dayStart + DAY_MS;

        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            List<Programme> list = parsed.programmes.get(id);
            if (list == null) continue;
            for (Programme programme : list) {
                if (programme.start < dayStart || programme.start >= dayEnd) continue;
                String dedupeKey = programme.start + "|" + programme.title;
                if (!seen.add(dedupeKey)) continue;
                result.add(new Epginfo(date, programme.title, date,
                        formatTime(programme.start), formatTime(programme.stop), result.size()));
            }
        }
        Collections.sort(result, new Comparator<Epginfo>() {
            @Override
            public int compare(Epginfo a, Epginfo b) {
                return a.startdateTime.compareTo(b.startdateTime);
            }
        });
        for (int i = 0; i < result.size(); i++) {
            result.get(i).index = i;
        }
        return result;
    }

    private static void addChannelId(Parsed parsed, Set<String> ids, String candidate) {
        if (candidate == null || candidate.isEmpty()) return;
        String key = normalize(candidate);
        if (key.isEmpty()) return;
        String id = parsed.keyToId.get(key);
        if (id != null) {
            ids.add(id);
            return;
        }
        // The candidate may already be a channel id that has no programmes
        // (or no display-name), look it up case sensitively as a fallback.
        if (parsed.programmes.containsKey(candidate)) ids.add(candidate);
    }

    private static void registerKey(Map<String, String> map, String key, String id) {
        String normalized = normalize(key);
        if (!normalized.isEmpty() && !map.containsKey(normalized)) {
            map.put(normalized, id);
        }
    }

    static String normalize(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private static String attr(String attrs, String name) {
        if (attrs == null) return null;
        String needle = name + "=\"";
        int start = attrs.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        int end = attrs.indexOf('"', start);
        if (end < 0) return null;
        return unescape(attrs.substring(start, end)).trim();
    }

    private static String tagText(String body, String tag) {
        int start = body.indexOf("<" + tag);
        if (start < 0) return null;
        int open = body.indexOf('>', start);
        if (open < 0) return null;
        if (open > start && body.charAt(open - 1) == '/') return null;
        int close = body.indexOf("</" + tag + ">", open);
        if (close < 0) return null;
        return unescape(body.substring(open + 1, close)).trim();
    }

    static String unescape(String value) {
        if (value == null || value.indexOf('&') < 0) return value;
        StringBuilder builder = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch != '&') {
                builder.append(ch);
                i++;
                continue;
            }
            int semi = value.indexOf(';', i + 1);
            if (semi < 0) {
                builder.append(ch);
                i++;
                continue;
            }
            String entity = value.substring(i + 1, semi);
            if (entity.equals("amp")) builder.append('&');
            else if (entity.equals("lt")) builder.append('<');
            else if (entity.equals("gt")) builder.append('>');
            else if (entity.equals("quot")) builder.append('"');
            else if (entity.equals("apos")) builder.append('\'');
            else if (entity.startsWith("#x") || entity.startsWith("#X")) {
                try {
                    builder.appendCodePoint(Integer.parseInt(entity.substring(2), 16));
                } catch (Exception e) {
                    builder.append(value, i, semi + 1);
                }
            } else if (entity.startsWith("#")) {
                try {
                    builder.appendCodePoint(Integer.parseInt(entity.substring(1)));
                } catch (Exception e) {
                    builder.append(value, i, semi + 1);
                }
            } else {
                builder.append(value, i, semi + 1);
            }
            i = semi + 1;
        }
        return builder.toString();
    }

    static long parseTime(String value) {
        if (value == null) return -1;
        String text = value.trim();
        if (text.length() < 14) return -1;
        String digits = text.substring(0, 14);
        int offsetMinutes = 8 * 60;
        String tail = text.substring(14).trim();
        if (tail.length() >= 3) {
            char sign = tail.charAt(0);
            if (sign == '+' || sign == '-') {
                try {
                    int hours = Integer.parseInt(tail.substring(1, 3));
                    int minutes = tail.length() >= 5 ? Integer.parseInt(tail.substring(3, 5)) : 0;
                    offsetMinutes = hours * 60 + minutes;
                    if (sign == '-') offsetMinutes = -offsetMinutes;
                } catch (Exception ignored) {
                }
            }
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            format.setLenient(false);
            format.setTimeZone(offsetTimeZone(offsetMinutes));
            Date date = format.parse(digits);
            return date == null ? -1 : date.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private static TimeZone offsetTimeZone(int offsetMinutes) {
        StringBuilder id = new StringBuilder("GMT");
        if (offsetMinutes >= 0) id.append('+');
        else {
            id.append('-');
            offsetMinutes = -offsetMinutes;
        }
        id.append(String.format(Locale.US, "%02d:%02d", offsetMinutes / 60, offsetMinutes % 60));
        return TimeZone.getTimeZone(id.toString());
    }

    private static String formatTime(long millis) {
        // Epginfo expects "HH:mm" (it appends ":00" itself).
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.US);
        format.setTimeZone(CST);
        return format.format(new Date(millis));
    }
}
