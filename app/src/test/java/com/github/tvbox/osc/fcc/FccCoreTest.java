package com.github.tvbox.osc.fcc;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone verification harness for the pure-Java FCC core.
 * Run with: java -cp out com.github.tvbox.osc.fcc.FccCoreTest
 */
public class FccCoreTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testUrlParsing();
        testPacketBuilders();
        testControlParsing();
        testRtpParsing();
        testReorder();
        testTelecomStateMachine();
        testHuaweiControl();
        testTelecomEndToEnd();
        testHuaweiEndToEnd();
        if (failures == 0) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // URL parsing
    // ------------------------------------------------------------------

    private static void testUrlParsing() {
        FccChannel c = FccUrlParser.parse("rtp://239.11.0.61:5140?fcc=183.221.1.148:8027");
        assertNotNull("rtp url parsed", c);
        assertEquals("mcast ip", "239.11.0.61", c.multicastIp);
        assertEquals("mcast port", 5140, c.multicastPort);
        assertEquals("fcc ip", "183.221.1.148", c.fccIp);
        assertEquals("fcc port", 8027, c.fccPort);
        assertEquals("type", FccType.TELECOM, c.type);
        assertEquals("proxied", false, c.proxied);

        c = FccUrlParser.parse("http://192.168.7.1:5140/udp/239.11.0.61:5140?fcc=183.221.1.148:8027&fcc-type=huawei");
        assertNotNull("proxy url parsed", c);
        assertEquals("proxy mcast", "239.11.0.61", c.multicastIp);
        assertEquals("proxy type", FccType.HUAWEI, c.type);
        assertEquals("proxied flag", true, c.proxied);

        c = FccUrlParser.parse("udp://@239.1.2.3:5000?fcc=10.0.0.1");
        assertNotNull("udp @ url parsed", c);
        assertEquals("default fcc port", 8027, c.fccPort);

        c = FccUrlParser.parse("igmp://239.1.2.3:5000?fcc=10.0.0.1:15970");
        assertNotNull("igmp url parsed", c);
        assertEquals("igmp fcc port", 15970, c.fccPort);

        assertNull("no fcc param", FccUrlParser.parse("rtp://239.1.2.3:5000"));
        assertNull("not multicast", FccUrlParser.parse("rtp://10.0.0.1:5000?fcc=10.0.0.1:8027"));
        assertNull("http without path", FccUrlParser.parse("http://192.168.1.1:5140/?fcc=10.0.0.1:8027"));
        System.out.println("url parsing OK");
    }

    // ------------------------------------------------------------------
    // Packet builders
    // ------------------------------------------------------------------

    private static void testPacketBuilders() throws Exception {
        InetAddress mcast = InetAddress.getByName("239.11.0.61");
        byte[] req = FccPackets.buildTelecomRequest(mcast, 5140, 12345);
        assertEquals("telecom req len", 40, req.length);
        assertEquals("telecom fmt", 0x82, req[0] & 0xFF);
        assertEquals("telecom pt", 205, req[1] & 0xFF);
        assertEquals("telecom words", 9, ((req[2] & 0xFF) << 8) | (req[3] & 0xFF));
        assertEquals("telecom ssrc0", 239, req[8] & 0xFF);
        assertEquals("telecom ssrc1", 11, req[9] & 0xFF);
        assertEquals("telecom client port", 12345, ((req[16] & 0xFF) << 8) | (req[17] & 0xFF));
        assertEquals("telecom group port", 5140, ((req[18] & 0xFF) << 8) | (req[19] & 0xFF));
        assertEquals("telecom group ip", 61, req[23] & 0xFF);

        byte[] term = FccPackets.buildTelecomTermination(mcast, 0x1234);
        assertEquals("telecom term len", 16, term.length);
        assertEquals("telecom term fmt", 0x85, term[0] & 0xFF);
        assertEquals("telecom term stop bit", 0, term[12] & 0xFF);
        assertEquals("telecom term seq", 0x1234, ((term[14] & 0xFF) << 8) | (term[15] & 0xFF));

        byte[] force = FccPackets.buildTelecomTermination(mcast, 0);
        assertEquals("telecom force stop bit", 1, force[12] & 0xFF);

        InetAddress local = InetAddress.getByName("192.168.7.100");
        byte[] hw = FccPackets.buildHuaweiRequest(mcast, local, 20001);
        assertEquals("huawei req len", 32, hw.length);
        assertEquals("huawei fmt", 0x85, hw[0] & 0xFF);
        assertEquals("huawei words", 7, ((hw[2] & 0xFF) << 8) | (hw[3] & 0xFF));
        assertEquals("huawei local ip", 192, hw[20] & 0xFF);
        assertEquals("huawei local ip4", 100, hw[23] & 0xFF);
        assertEquals("huawei client port", 20001, ((hw[24] & 0xFF) << 8) | (hw[25] & 0xFF));
        assertEquals("huawei flag", 0x8000, ((hw[26] & 0xFF) << 8) | (hw[27] & 0xFF));
        assertEquals("huawei redirect flag", 0x20, hw[28] & 0xFF);

        byte[] nat = FccPackets.buildHuaweiNat(0xCAFEBABEL);
        assertEquals("nat len", 8, nat.length);
        assertEquals("nat marker", 0x03, nat[1] & 0xFF);
        assertEquals("nat session", 0xCA, nat[4] & 0xFF);

        byte[] hwTerm = FccPackets.buildHuaweiTermination(mcast, 77);
        assertEquals("huawei term fmt", 0x89, hwTerm[0] & 0xFF);
        assertEquals("huawei term status", 1, hwTerm[12] & 0xFF);
        assertEquals("huawei term seq", 77, ((hwTerm[14] & 0xFF) << 8) | (hwTerm[15] & 0xFF));

        byte[] hwErr = FccPackets.buildHuaweiTermination(mcast, 0);
        assertEquals("huawei err status", 2, hwErr[12] & 0xFF);
        System.out.println("packet builders OK");
    }

    // ------------------------------------------------------------------
    // Control parsing
    // ------------------------------------------------------------------

    private static void testControlParsing() throws Exception {
        byte[] resp = new byte[36];
        resp[0] = (byte) 0x83;
        resp[1] = (byte) 205;
        FccPackets.putU16(resp, 2, 8);
        resp[12] = 0;
        resp[13] = 2;
        FccPackets.putU16(resp, 16, 30000);
        FccControl c = FccPackets.parse(FccType.TELECOM, resp, resp.length);
        assertNotNull("telecom response parsed", c);
        assertEquals("telecom kind", FccControl.KIND_TELECOM_RESPONSE, c.kind);
        assertEquals("telecom action", 2, c.actionType);
        assertEquals("telecom media port", 30000, c.mediaPort);

        byte[] sync = new byte[12];
        sync[0] = (byte) 0x84;
        sync[1] = (byte) 205;
        FccPackets.putU16(sync, 2, 2);
        c = FccPackets.parse(FccType.TELECOM, sync, sync.length);
        assertEquals("sync kind", FccControl.KIND_SYNC, c.kind);

        byte[] hw = new byte[24];
        hw[0] = (byte) 0x86;
        hw[1] = (byte) 205;
        FccPackets.putU16(hw, 2, 5);
        hw[12] = 1;
        FccPackets.putU16(hw, 14, 2);
        FccPackets.putU16(hw, 16, 500);
        FccPackets.putU16(hw, 20, 8000);
        c = FccPackets.parse(FccType.HUAWEI, hw, hw.length);
        assertEquals("huawei kind", FccControl.KIND_HUAWEI_RESPONSE, c.kind);
        assertEquals("huawei result", 1, c.resultCode);
        assertEquals("huawei first seq", 500, c.firstSeq);
        assertEquals("huawei bitrate", 8000, c.bitrateKbps);

        byte[] garbage = new byte[]{0x47, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x01, 0x02, 0x03, 0x04};
        assertNull("garbage is not rtcp", FccPackets.parse(FccType.TELECOM, garbage, garbage.length));
        System.out.println("control parsing OK");
    }

    // ------------------------------------------------------------------
    // RTP parsing
    // ------------------------------------------------------------------

    private static void testRtpParsing() {
        byte[] rtp = makePacket(100, 188);
        RtpPacket p = RtpPacket.parse(rtp, rtp.length);
        assertNotNull("rtp parsed", p);
        assertEquals("rtp flag", true, p.rtp);
        assertEquals("rtp seq", 100, p.seq);
        assertEquals("rtp length", 188, p.length);
        assertEquals("ts sync", 0x47, p.payload[p.offset] & 0xFF);
        assertEquals("marker seq", 100, ((p.payload[1] & 0xFF) << 8) | (p.payload[2] & 0xFF));

        byte[] fec = makePacket(7, 10);
        fec[1] = (byte) (fec[1] | 127);
        p = RtpPacket.parse(fec, fec.length);
        assertNotNull("fec parsed", p);
        assertEquals("fec flag", true, p.fec);
        assertEquals("fec seq", 7, p.seq);

        byte[] ts = new byte[188];
        ts[0] = 0x47;
        p = RtpPacket.parse(ts, ts.length);
        assertNotNull("bare ts parsed", p);
        assertEquals("bare ts not rtp", false, p.rtp);
        assertEquals("bare ts length", 188, p.length);

        byte[] junk = new byte[]{1, 2, 3, 4};
        assertNull("junk dropped", RtpPacket.parse(junk, junk.length));
        System.out.println("rtp parsing OK");
    }

    // ------------------------------------------------------------------
    // Reorder buffer
    // ------------------------------------------------------------------

    private static void testReorder() {
        RtpReorder reorder = new RtpReorder();
        final List<Integer> delivered = new ArrayList<>();
        RtpReorder.Deliver deliver = new RtpReorder.Deliver() {
            @Override
            public void deliver(RtpPacket packet) {
                delivered.add(packet.seq);
            }
        };
        for (int i = 0; i < 8; i++) reorder.insert(rtpPacket(1000 + i), deliver);
        assertEquals("reorder initial count", 8, delivered.size());
        assertEquals("reorder initial first", 1000, delivered.get(0).intValue());
        assertEquals("reorder initial last", 1007, delivered.get(7).intValue());

        reorder.insert(rtpPacket(1009), deliver);
        reorder.insert(rtpPacket(1008), deliver);
        assertEquals("reorder recovers hole", 10, delivered.size());
        assertEquals("reorder recovered", 1008, delivered.get(8).intValue());
        assertEquals("reorder recovered next", 1009, delivered.get(9).intValue());

        reorder.insert(rtpPacket(1008), deliver);
        assertEquals("reorder drops duplicate", 10, delivered.size());

        reorder.insert(rtpPacket(999), deliver);
        assertEquals("reorder drops late", 10, delivered.size());

        RtpReorder force = new RtpReorder();
        final List<Integer> forced = new ArrayList<>();
        RtpReorder.Deliver forcedDeliver = new RtpReorder.Deliver() {
            @Override
            public void deliver(RtpPacket packet) {
                forced.add(packet.seq);
            }
        };
        for (int i = 0; i < 8; i++) force.insert(rtpPacket(2000 + i), forcedDeliver);
        for (int i = 9; i < 120; i++) force.insert(rtpPacket(2000 + i), forcedDeliver);
        assertTrue("force flush skipped lost packet", !forced.contains(2008));
        assertTrue("force flush delivered later packets", forced.contains(2009) && forced.size() > 8);

        RtpReorder wrap = new RtpReorder();
        final List<Integer> wrapped = new ArrayList<>();
        RtpReorder.Deliver wrapDeliver = new RtpReorder.Deliver() {
            @Override
            public void deliver(RtpPacket packet) {
                wrapped.add(packet.seq);
            }
        };
        for (int i = 0; i < 20; i++) wrap.insert(rtpPacket((65530 + i) & 0xFFFF), wrapDeliver);
        assertEquals("wrap delivers all", 20, wrapped.size());
        assertEquals("wrap first", 65530, wrapped.get(0).intValue());
        assertEquals("wrap last", 13, wrapped.get(19).intValue());
        System.out.println("reorder OK");
    }

    // ------------------------------------------------------------------
    // State machine (no sockets)
    // ------------------------------------------------------------------

    private static void testTelecomStateMachine() throws Exception {
        FccChannel channel = new FccChannel("test", "239.255.42.99", randomPort(),
                "127.0.0.1", randomPort(), FccType.TELECOM, false);
        ByteQueue queue = new ByteQueue(4 * 1024 * 1024);
        FccSession session = new FccSession(channel, queue, silentLogger(), null);
        assertEquals("initial state", FccSession.State.INIT, session.getState());

        session.setState(FccSession.State.REQUESTED, "test request");
        session.handleControl(telecomResponse(0, 2));
        assertEquals("accepted state", FccSession.State.UNICAST_PENDING, session.getState());

        for (int i = 0; i < 8; i++) session.handleUnicast(rtpPacket(1000 + i));
        assertEquals("unicast active", FccSession.State.UNICAST_ACTIVE, session.getState());
        List<Integer> markers = readMarkers(queue, 8);
        assertEquals("unicast delivered", 8, markers.size());
        assertEquals("unicast first", 1000, markers.get(0).intValue());

        session.handleControl(telecomSync());
        assertEquals("mcast requested", FccSession.State.MCAST_REQUESTED, session.getState());

        for (int i = 0; i < 10; i++) session.handleMulticast(rtpPacket(1010 + i));
        // First multicast packet sets term seqn = 1012; unicast must reach 1011.
        for (int i = 8; i < 20; i++) session.handleUnicast(rtpPacket(1000 + i));
        assertEquals("switched to multicast", FccSession.State.MCAST_ACTIVE, session.getState());
        List<Integer> rest = readMarkers(queue, 100, 600);
        assertTrue("output continues after switch, size=" + rest.size(), rest.size() >= 12);
        assertEquals("tail reaches multicast", 1019, rest.get(rest.size() - 1).intValue());

        byte[] bareTs = new byte[188];
        bareTs[0] = 0x47;
        bareTs[1] = (byte) 0xAB;
        bareTs[2] = (byte) 0xCD;
        session.handleMulticast(RtpPacket.parse(bareTs, bareTs.length));
        List<Integer> bareMarkers = readMarkers(queue, 1, 500);
        assertEquals("bare ts passthrough", 0xABCD, bareMarkers.get(0).intValue());
        session.stop();
        System.out.println("telecom state machine OK");
    }

    private static void testHuaweiControl() throws Exception {
        byte[] hw = new byte[36];
        hw[0] = (byte) 0x86;
        hw[1] = (byte) 205;
        FccPackets.putU16(hw, 2, 8);
        hw[12] = 1;
        FccPackets.putU16(hw, 14, 2);
        FccPackets.putU16(hw, 16, 500);
        FccPackets.putU16(hw, 20, 8000);
        hw[24] = 0x20; // bit 5 set -> natFlag 1
        FccPackets.putU16(hw, 26, 9000);
        FccPackets.putU32(hw, 28, 0x12345678L);
        FccPackets.putU32(hw, 32, 0x0A000001L);
        FccControl c = FccPackets.parse(FccType.HUAWEI, hw, hw.length);
        assertEquals("huawei nat flag", 1, c.natFlag);
        assertEquals("huawei nat port", 9000, c.natServerPort);
        assertEquals("huawei nat ip", "10.0.0.1", c.natServerIp);
        assertEquals("huawei session", 0x12345678L, c.sessionId);
        System.out.println("huawei control OK");
    }

    // ------------------------------------------------------------------
    // End to end with a fake FCC server
    // ------------------------------------------------------------------

    private static void testTelecomEndToEnd() throws Exception {
        runEndToEnd(FccType.TELECOM, false);
        System.out.println("telecom end-to-end OK");
    }

    private static void testHuaweiEndToEnd() throws Exception {
        runEndToEnd(FccType.HUAWEI, true);
        System.out.println("huawei end-to-end OK");
    }

    private static void runEndToEnd(final FccType type, boolean huawei) throws Exception {
        final int fccPort = randomPort();
        final int mcastPort = randomPort();
        final int unicastStart = 1000;
        final int mcastStart = 1010;
        final int packets = 70;
        final FakeFccServer server = new FakeFccServer(fccPort, mcastPort, type, huawei,
                unicastStart, mcastStart, packets);
        server.start();

        FccChannel channel = new FccChannel("test", "239.255.42.99", mcastPort,
                "127.0.0.1", fccPort, type, false);
        ByteQueue queue = new ByteQueue(8 * 1024 * 1024);
        final List<String> events = new ArrayList<>();
        FccSession session = new FccSession(channel, queue, silentLogger(), new FccSessionListener() {
            @Override
            public void onFccActive() {
                synchronized (events) {
                    events.add("active");
                }
            }

            @Override
            public void onFccFallback(String reason) {
                synchronized (events) {
                    events.add("fallback:" + reason);
                }
            }

            @Override
            public void onFatal(String reason) {
                synchronized (events) {
                    events.add("fatal:" + reason);
                }
            }
        });
        session.start();

        List<Integer> markers = readMarkers(queue, 1000, 9000);
        session.stop();
        server.shutdown();

        assertTrue("e2e got data", markers.size() >= 20);
        for (int i = 0; i < 5; i++) {
            assertEquals("e2e first marker " + i, unicastStart + i, markers.get(i).intValue());
        }
        int max = 0;
        for (Integer m : markers) {
            assertTrue("e2e marker in range: " + m, m >= unicastStart && m <= mcastStart + packets);
            max = Math.max(max, m);
        }
        assertTrue("e2e reached tail, max=" + max, max >= mcastStart + packets - 10);
        assertTrue("e2e fcc active event", events.contains("active"));
        assertTrue("e2e termination seqn=" + server.termSeq, server.termSeq >= mcastStart && server.termSeq <= mcastStart + 20);
        assertEquals("e2e state", FccSession.State.MCAST_ACTIVE, session.getState());
    }

    private static final class FakeFccServer extends Thread {
        private final int fccPort;
        private final int mcastPort;
        private final FccType type;
        private final boolean huawei;
        private final int unicastStart;
        private final int mcastStart;
        private final int packets;
        private final DatagramSocket socket;
        private volatile boolean running = true;
        volatile int termSeq = -1;
        private InetAddress clientAddress;
        private int clientPort;
        private boolean started;

        FakeFccServer(int fccPort, int mcastPort, FccType type, boolean huawei,
                      int unicastStart, int mcastStart, int packets) throws Exception {
            this.fccPort = fccPort;
            this.mcastPort = mcastPort;
            this.type = type;
            this.huawei = huawei;
            this.unicastStart = unicastStart;
            this.mcastStart = mcastStart;
            this.packets = packets;
            this.socket = new DatagramSocket(fccPort);
            this.socket.setSoTimeout(200);
            setName("fake-fcc");
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buffer = new byte[2048];
            long deadline = System.currentTimeMillis() + 10000;
            try {
                while (running && System.currentTimeMillis() < deadline) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (Exception e) {
                        continue;
                    }
                    int len = packet.getLength();
                    if (len <= 0) continue;
                    int fmt = buffer[0] & 0x1F;
                    if (System.getenv("FCC_DEBUG") != null) System.out.println("server recv len=" + len + " fmt=" + fmt + " from=" + packet.getAddress());
                    boolean isRequest = (!huawei && fmt == 2 && len >= 16)
                            || (huawei && fmt == 5 && len >= 32);
                    if (System.getenv("FCC_DEBUG") != null) System.out.println("server isRequest=" + isRequest + " len=" + len);
                    if (isRequest) {
                        clientAddress = packet.getAddress();
                        int offset = huawei ? 24 : 16;
                        clientPort = ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
                        if (System.getenv("FCC_DEBUG") != null) System.out.println("server responding to " + clientAddress + ":" + clientPort);
                        sendResponse();
                        if (!started) {
                            started = true;
                            startMediaThread();
                        }
                    } else if ((!huawei && fmt == 5 && len >= 16) || (huawei && fmt == 9 && len >= 16)) {
                        termSeq = ((buffer[14] & 0xFF) << 8) | (buffer[15] & 0xFF);
                    }
                    buffer = new byte[2048];
                }
            } catch (Exception ignored) {
            }
        }

        private void sendResponse() throws Exception {
            byte[] response;
            if (huawei) {
                response = new byte[24];
                response[0] = (byte) 0x86;
                response[1] = (byte) 205;
                FccPackets.putU16(response, 2, 5);
                response[12] = 1;
                FccPackets.putU16(response, 14, 2);
                FccPackets.putU16(response, 16, unicastStart);
                FccPackets.putU16(response, 20, 8000);
            } else {
                response = new byte[36];
                response[0] = (byte) 0x83;
                response[1] = (byte) 205;
                FccPackets.putU16(response, 2, 8);
                response[12] = 0;
                response[13] = 2;
            }
            socket.send(new DatagramPacket(response, response.length, clientAddress, clientPort));
        }

        private Thread mediaThread;
        private Thread mcastThread;

        private void startMediaThread() {
            Thread media = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sendUnicast();
                    } catch (Exception e) {
                        if (System.getenv("FCC_DEBUG") != null) System.out.println("media thread error: " + e);
                        e.printStackTrace();
                    }
                }
            }, "fake-fcc-media");
            media.setDaemon(true);
            mediaThread = media;
            media.start();
        }

        private void sendUnicast() throws Exception {
            DatagramSocket sender = new DatagramSocket();
            try {
                for (int i = 0; i < packets && running; i++) {
                    int seq = unicastStart + i;
                    byte[] payload = makePacket(seq, 188);
                    if (System.getenv("FCC_DEBUG") != null) System.out.println("media send seq=" + seq + " to " + clientAddress + ":" + clientPort);
                    sender.send(new DatagramPacket(payload, payload.length, clientAddress, clientPort));
                    if (i == 3) {
                        byte[] sync = new byte[12];
                        sync[0] = (byte) (huawei ? 0x88 : 0x84);
                        sync[1] = (byte) 205;
                        FccPackets.putU16(sync, 2, 2);
                        socket.send(new DatagramPacket(sync, sync.length, clientAddress, clientPort));
                        startMulticast(sender);
                    }
                    Thread.sleep(2);
                }
            } finally {
                sender.close();
            }
        }

        private void startMulticast(final DatagramSocket sender) {
            Thread mcast = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < packets && running; i++) {
                            int seq = mcastStart + i;
                            byte[] payload = makePacket(seq, 188);
                            sender.send(new DatagramPacket(payload, payload.length,
                                    InetAddress.getByName("127.0.0.1"), mcastPort));
                            Thread.sleep(1);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, "fake-fcc-mcast");
            mcast.setDaemon(true);
            mcastThread = mcast;
            mcast.start();
        }

        void shutdown() {
            running = false;
            socket.close();
            if (mediaThread != null) {
                try { mediaThread.join(1000); } catch (InterruptedException ignored) { }
            }
            if (mcastThread != null) {
                try { mcastThread.join(1000); } catch (InterruptedException ignored) { }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static RtpPacket rtpPacket(int seq) {
        byte[] data = makePacket(seq, 188);
        return RtpPacket.parse(data, data.length);
    }

    static byte[] makePacket(int seq, int payloadLength) {
        byte[] payload = new byte[payloadLength];
        payload[0] = 0x47;
        payload[1] = (byte) ((seq >> 8) & 0xFF);
        payload[2] = (byte) (seq & 0xFF);
        for (int i = 3; i < payloadLength; i++) payload[i] = (byte) i;
        byte[] rtp = new byte[12 + payloadLength];
        rtp[0] = (byte) 0x80;
        rtp[1] = 33; // MP2T
        rtp[2] = (byte) ((seq >> 8) & 0xFF);
        rtp[3] = (byte) (seq & 0xFF);
        System.arraycopy(payload, 0, rtp, 12, payloadLength);
        return rtp;
    }

    private static FccControl telecomResponse(int result, int type) {
        byte[] response = new byte[36];
        response[0] = (byte) 0x83;
        response[1] = (byte) 205;
        FccPackets.putU16(response, 2, 8);
        response[12] = (byte) result;
        response[13] = (byte) type;
        return FccPackets.parse(FccType.TELECOM, response, response.length);
    }

    private static FccControl telecomSync() {
        byte[] sync = new byte[12];
        sync[0] = (byte) 0x84;
        sync[1] = (byte) 205;
        FccPackets.putU16(sync, 2, 2);
        return FccPackets.parse(FccType.TELECOM, sync, sync.length);
    }

    private static List<Integer> readMarkers(ByteQueue queue, int count) throws Exception {
        return readMarkers(queue, count, 4000);
    }

    private static List<Integer> readMarkers(ByteQueue queue, int count, long timeoutMs) throws Exception {
        List<Integer> markers = new ArrayList<>();
        byte[] packet = new byte[188];
        long deadline = System.currentTimeMillis() + timeoutMs;
        int emptyReads = 0;
        while (markers.size() < count && System.currentTimeMillis() < deadline) {
            int n = queue.read(packet, 0, 188, 700);
            if (n < 0) {
                if (++emptyReads >= 2) break;
                continue;
            }
            int read = n;
            while (read < 188) {
                int more = queue.read(packet, read, 188 - read, 700);
                if (more < 0) break;
                read += more;
            }
            if (read < 188) break;
            emptyReads = 0;
            assertEquals("ts sync byte", 0x47, packet[0] & 0xFF);
            markers.add(((packet[1] & 0xFF) << 8) | (packet[2] & 0xFF));
        }
        return markers;
    }

    private static int randomPort() throws Exception {
        DatagramSocket socket = new DatagramSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private static FccLogger silentLogger() {
        final boolean debug = System.getenv("FCC_DEBUG") != null;
        return new FccLogger() {
            @Override
            public void d(String message) {
                if (debug) System.out.println("[debug] " + message);
            }

            @Override
            public void w(String message) {
                System.out.println("[warn] " + message);
            }

            @Override
            public void e(String message) {
                System.out.println("[error] " + message);
            }
        };
    }

    private static void assertTrue(String name, boolean condition) {
        if (condition) {
            System.out.println("  ok: " + name);
        } else {
            failures++;
            System.out.println("  FAIL: " + name);
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        assertEquals(name, String.valueOf(expected), String.valueOf(actual));
    }

    private static void assertEquals(String name, int expected, int actual) {
        assertEquals(name, String.valueOf(expected), String.valueOf(actual));
    }

    private static void assertEquals(String name, boolean expected, boolean actual) {
        assertEquals(name, String.valueOf(expected), String.valueOf(actual));
    }

    private static void assertEquals(String name, String expected, String actual) {
        if (expected.equals(actual)) {
            System.out.println("  ok: " + name);
        } else {
            failures++;
            System.out.println("  FAIL: " + name + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertNull(String name, Object value) {
        assertTrue(name, value == null);
    }

    private static void assertNotNull(String name, Object value) {
        assertTrue(name, value != null);
    }
}
