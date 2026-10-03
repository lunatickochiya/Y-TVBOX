package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Epginfo;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Standalone XMLTV parser check. Point it at a generated iptv_epg.xml[.gz]:
 *
 * <pre>
 * javac -d out XmltvEpgParser.java Epginfo.java XmltvParserTest.java
 * java -cp out com.github.tvbox.osc.util.XmltvParserTest /path/to/iptv_epg.xml.gz [yyyy-MM-dd]
 * </pre>
 */
public class XmltvParserTest {

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "/tmp/iptv_epg.xml";
        String day = args.length > 1 ? args[1] : new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        byte[] data = readFile(path);
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            data = gunzip(data);
        }
        String xml = new String(data, "UTF-8");

        long start = System.currentTimeMillis();
        XmltvEpgParser.Parsed parsed = XmltvEpgParser.parse(xml);
        System.out.println("parsed in " + (System.currentTimeMillis() - start) + " ms, channels="
                + parsed.keyToId.size() + ", channelsWithProgrammes=" + parsed.programmes.size());

        Date date = new SimpleDateFormat("yyyy-MM-dd").parse(day);
        check(parsed, "CCTV4K", "CCTV4K", date);
        check(parsed, "CCTV1", "CCTV1", date);
        check(parsed, "湖南卫视", "湖南卫视", date);
        check(parsed, "睛彩青少", null, date);
        check(parsed, "不存在的频道", null, date);
    }

    private static void check(XmltvEpgParser.Parsed parsed, String name, String tag, Date day) {
        List<Epginfo> list = XmltvEpgParser.query(parsed, name, tag, day);
        if (list.isEmpty()) {
            System.out.println("  " + name + " (" + tag + ") -> 0");
            return;
        }
        System.out.println("  " + name + " (" + tag + ") -> " + list.size()
                + " | " + list.get(0).start + "-" + list.get(0).end + " " + list.get(0).title
                + " | ... " + list.get(list.size() - 1).start + " " + list.get(list.size() - 1).title);
    }

    private static byte[] readFile(String path) throws Exception {
        InputStream in = new FileInputStream(path);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static byte[] gunzip(byte[] data) throws Exception {
        GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(data));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = gzip.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally {
            gzip.close();
        }
    }
}
