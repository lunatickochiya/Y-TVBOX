package com.github.tvbox.osc.fcc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

/**
 * FCC + multicast receive session.
 *
 * <p>This is a Java port of the relevant parts of rtp2httpd's stream layer:
 * <ol>
 *   <li>Send a telecom (RTCP FMT 2) or Huawei (FMT 5) FCC request to the
 *       operator FCC server.</li>
 *   <li>Forward the accelerated unicast burst, reordered, to the output.</li>
 *   <li>On the server's sync notification, join the multicast group and keep
 *       buffering multicast packets while the unicast burst catches up.</li>
 *   <li>Tell the server which multicast sequence we start from (termination
 *       packet) and switch the output to the multicast stream seamlessly.</li>
 * </ol>
 *
 * <p>When anything in the FCC path fails the session falls back to plain
 * multicast so playback still works (just without the fast start).
 *
 * <p>Each socket has its own receiver thread so a busy socket can never delay
 * timeout processing or packets arriving on another socket. All packet
 * handling and the periodic tick run under one lock, which keeps the state
 * machine race free.
 *
 * <p>The output is a {@link ByteQueue} carrying the RTP payload (MPEG-TS)
 * bytes, consumed by the local HTTP relay in {@link FccRelayServer}.
 */
public final class FccSession {

    public enum State {
        INIT, REQUESTED, UNICAST_PENDING, UNICAST_ACTIVE, MCAST_REQUESTED, MCAST_ACTIVE, ERROR
    }

    private static final int SIGNALING_TIMEOUT_MS = 80;
    private static final int UNICAST_TIMEOUT_MS = 1000;
    private static final int SYNC_WAIT_TIMEOUT_MS = 15000;
    private static final int MCAST_FIRST_PACKET_TIMEOUT_MS = 8000;
    private static final int MCAST_STALL_TIMEOUT_MS = 10000;
    private static final int TOTAL_START_TIMEOUT_MS = 8000;
    private static final int SOCKET_RECEIVE_BUFFER = 4 * 1024 * 1024;
    private static final int TICK_INTERVAL_MS = 30;
    private static final int MAX_PENDING_MCAST_BYTES = 12 * 1024 * 1024;

    private final FccChannel channel;
    private final FccLogger log;
    private final ByteQueue sink;
    private final FccSessionListener listener;

    private volatile boolean running;
    private Thread controlThread;
    private final List<Thread> workers = new ArrayList<>();
    private final Object packetLock = new Object();
    private volatile State state = State.INIT;

    private DatagramSocket signalSocket;
    private DatagramSocket mediaSocket;
    private MulticastSocket multicastSocket;

    private InetAddress multicastAddress;
    private InetAddress fccServer;
    private int fccServerPort;
    private NetworkInterface networkInterface;
    private int huaweiMediaPort;
    private boolean verifyServerIp;
    private int redirectCount;
    private long sessionId;
    private boolean needNatTraversal;

    private long startTime;
    private long lastFccDataTime;
    private long unicastStartTime;
    private long mcastJoinTime;
    private long lastMcastDataTime;
    private boolean mcastJoined;
    private boolean switched;
    private boolean sawUnicastData;
    private int firstMcastSeq = -1;
    private int termSeqs = -1;
    private boolean termSent;

    private final RtpReorder unicastReorder = new RtpReorder();
    private final RtpReorder multicastReorder = new RtpReorder();
    private final ArrayList<RtpPacket> pendingMcast = new ArrayList<>();
    private long pendingMcastBytes;

    private final RtpReorder.Deliver unicastDeliver = new RtpReorder.Deliver() {
        @Override
        public void deliver(RtpPacket packet) {
            writePayload(packet);
        }
    };

    private final RtpReorder.Deliver multicastDeliver = new RtpReorder.Deliver() {
        @Override
        public void deliver(RtpPacket packet) {
            if (switched) {
                writePayload(packet);
            } else {
                bufferPendingMcast(packet);
            }
        }
    };

    public FccSession(FccChannel channel, ByteQueue sink, FccLogger log, FccSessionListener listener) {
        this.channel = channel;
        this.sink = sink;
        this.log = log;
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;
        running = true;
        startTime = System.currentTimeMillis();
        controlThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runSession();
            }
        }, "fcc-control");
        controlThread.setDaemon(true);
        controlThread.start();
    }

    public void stop() {
        running = false;
        Thread control = controlThread;
        if (control != null) {
            try {
                control.join(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeSocketsQuietly();
        synchronized (workers) {
            for (Thread worker : workers) {
                try {
                    worker.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            workers.clear();
        }
    }

    // ------------------------------------------------------------------
    // Session thread
    // ------------------------------------------------------------------

    private void runSession() {
        try {
            if (!setupSockets()) {
                fatal("FCC setup failed");
                return;
            }
            sendRequest();
            lastFccDataTime = System.currentTimeMillis();
            setState(State.REQUESTED, "request sent");
            startWorker(signalSocket, true);
            if (mediaSocket != null && mediaSocket != signalSocket) {
                startWorker(mediaSocket, true);
            }
            while (running) {
                synchronized (packetLock) {
                    tick(System.currentTimeMillis());
                }
                try {
                    Thread.sleep(TICK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Throwable t) {
            log.e("FCC session error: " + t);
        } finally {
            cleanup();
        }
    }

    private boolean setupSockets() {
        try {
            multicastAddress = InetAddress.getByName(channel.multicastIp);
            fccServer = InetAddress.getByName(channel.fccIp);
            fccServerPort = channel.fccPort;
            networkInterface = pickNetworkInterface(fccServer);
            if (networkInterface == null) {
                log.w("FCC: no usable network interface, trying default route");
            }

            if (channel.type == FccType.HUAWEI) {
                if (!openHuaweiSockets()) {
                    log.e("FCC (Huawei): cannot bind media/signal port pair");
                    return false;
                }
                log.d("FCC: bound media port " + mediaSocket.getLocalPort()
                        + ", signal port " + signalSocket.getLocalPort());
            } else {
                signalSocket = new DatagramSocket();
                configure(signalSocket);
                mediaSocket = signalSocket;
                log.d("FCC: bound signal port " + signalSocket.getLocalPort());
            }
            return true;
        } catch (Exception e) {
            log.e("FCC: socket setup failed: " + e);
            return false;
        }
    }

    private void configure(DatagramSocket socket) {
        try {
            socket.setReceiveBufferSize(SOCKET_RECEIVE_BUFFER);
        } catch (Exception ignored) {
        }
    }

    private boolean openHuaweiSockets() {
        Random random = new Random();
        for (int attempt = 0; attempt < 30; attempt++) {
            int base = 20000 + random.nextInt(45000);
            if (base > 65533) continue;
            DatagramSocket media = null;
            DatagramSocket signal = null;
            try {
                media = new DatagramSocket(base);
                signal = new DatagramSocket(base + 1);
            } catch (Exception e) {
                if (media != null) media.close();
                if (signal != null) signal.close();
                continue;
            }
            configure(media);
            configure(signal);
            mediaSocket = media;
            signalSocket = signal;
            return true;
        }
        return false;
    }

    private NetworkInterface pickNetworkInterface(InetAddress target) {
        try {
            DatagramSocket probe = new DatagramSocket();
            try {
                probe.connect(target, 9);
                InetAddress local = probe.getLocalAddress();
                if (local != null && !local.isAnyLocalAddress()) {
                    NetworkInterface nif = NetworkInterface.getByInetAddress(local);
                    if (nif != null && nif.isUp()) return nif;
                }
            } finally {
                probe.close();
            }
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InterfaceAddress address : nif.getInterfaceAddresses()) {
                    if (address.getAddress() instanceof Inet4Address) return nif;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private InetAddress getLocalIpv4() {
        if (networkInterface != null) {
            for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                if (address.getAddress() instanceof Inet4Address) return address.getAddress();
            }
        }
        return null;
    }

    private void startWorker(final DatagramSocket socket, final boolean unicast) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[65536];
                while (running) {
                    try {
                        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                        socket.receive(datagram);
                        if (!running) break;
                        synchronized (packetLock) {
                            handleDatagram(unicast, buffer, datagram.getLength(),
                                    datagram.getAddress(), System.currentTimeMillis());
                        }
                    } catch (SocketException e) {
                        break;
                    } catch (IOException e) {
                        if (running) log.w("FCC: receive failed: " + e);
                    }
                }
            }
        }, unicast ? "fcc-rx-unicast" : "fcc-rx-multicast");
        worker.setDaemon(true);
        synchronized (workers) {
            workers.add(worker);
        }
        worker.start();
    }

    private void handleDatagram(boolean unicast, byte[] data, int len, InetAddress from, long now) {
        if (len <= 0) return;
        if (unicast) {
            if (verifyServerIp && !from.equals(fccServer)) return;
            lastFccDataTime = now;
            if (FccPackets.isRtcp(data, len)) {
                FccControl control = FccPackets.parse(channel.type, data, len);
                if (control != null) handleControl(control);
            } else {
                RtpPacket packet = RtpPacket.parse(data, len);
                if (packet != null && !packet.fec) handleUnicast(packet);
            }
        } else {
            lastMcastDataTime = now;
            RtpPacket packet = RtpPacket.parse(data, len);
            if (packet != null && !packet.fec) handleMulticast(packet);
        }
    }

    // ------------------------------------------------------------------
    // FCC protocol
    // ------------------------------------------------------------------

    private void sendRequest() {
        try {
            if (channel.type == FccType.HUAWEI) {
                InetAddress local = getLocalIpv4();
                if (local == null) {
                    log.e("FCC (Huawei): cannot determine local IP");
                    fallbackToMulticast("no local IP");
                    return;
                }
                byte[] packet = FccPackets.buildHuaweiRequest(multicastAddress, local, signalSocket.getLocalPort());
                sendTriple(signalSocket, packet, fccServer, fccServerPort);
                log.d("FCC (Huawei): request sent to " + channel.fccIp + ":" + fccServerPort
                        + " local " + local.getHostAddress() + ":" + signalSocket.getLocalPort());
            } else {
                byte[] packet = FccPackets.buildTelecomRequest(multicastAddress,
                        channel.multicastPort, signalSocket.getLocalPort());
                sendTriple(signalSocket, packet, fccServer, fccServerPort);
                log.d("FCC (Telecom): request sent to " + channel.fccIp + ":" + fccServerPort
                        + " client port " + signalSocket.getLocalPort());
            }
        } catch (Exception e) {
            log.e("FCC: request send failed: " + e);
            fallbackToMulticast("request failed");
        }
    }

    private void sendTermination(int seqn) {
        if (termSent || signalSocket == null || signalSocket.isClosed() || fccServer == null) return;
        try {
            byte[] packet;
            if (channel.type == FccType.HUAWEI) {
                packet = FccPackets.buildHuaweiTermination(multicastAddress, seqn);
            } else {
                packet = FccPackets.buildTelecomTermination(multicastAddress, seqn);
            }
            sendTriple(signalSocket, packet, fccServer, fccServerPort);
            termSent = true;
            log.d("FCC: termination packet sent, seqn=" + seqn);
        } catch (Exception e) {
            log.w("FCC: termination send failed: " + e);
        }
    }

    private void sendTriple(DatagramSocket socket, byte[] data, InetAddress address, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, 0, data.length, address, port);
        for (int i = 0; i < 3; i++) {
            socket.send(packet);
        }
    }

    private void sendEmpty(DatagramSocket socket, InetAddress address, int port) {
        if (socket == null || socket.isClosed() || address == null) return;
        try {
            socket.send(new DatagramPacket(new byte[0], 0, address, port));
        } catch (Exception ignored) {
        }
    }

    void handleControl(FccControl control) {
        if (control.kind == FccControl.KIND_SYNC) {
            onSyncNotification("sync notification received");
            return;
        }
        if (channel.type == FccType.HUAWEI) {
            handleHuaweiResponse(control);
        } else {
            handleTelecomResponse(control);
        }
    }

    private void handleTelecomResponse(FccControl c) {
        if (state != State.REQUESTED) return;

        if (c.resultCode != 0) {
            fallbackToMulticast("server error code " + c.resultCode);
            return;
        }

        boolean signalPortChanged = false;
        boolean mediaPortChanged = false;
        if (c.signalPort != 0 && c.signalPort != fccServerPort) {
            fccServerPort = c.signalPort;
            signalPortChanged = true;
        }
        if (c.mediaPort != 0 && c.mediaPort != huaweiMediaPort) {
            huaweiMediaPort = c.mediaPort;
            mediaPortChanged = true;
        }
        if (c.newIp != null) {
            try {
                InetAddress newServer = InetAddress.getByName(c.newIp);
                verifyServerIp = true;
                if (!newServer.equals(fccServer)) {
                    fccServer = newServer;
                    signalPortChanged = true;
                    mediaPortChanged = true;
                }
            } catch (Exception e) {
                log.w("FCC: bad redirect IP " + c.newIp);
            }
        }

        log.d("FCC (Telecom): result=" + c.resultCode + " type=" + c.actionType
                + " signalPort=" + c.signalPort + " mediaPort=" + c.mediaPort
                + " speed=" + c.speed + " speedAfterSync=" + c.speedAfterSync);

        if (c.actionType == 1) {
            fallbackToMulticast("no unicast needed");
        } else if (c.actionType == 2) {
            if (mediaPortChanged && huaweiMediaPort != 0) {
                sendEmpty(signalSocket, fccServer, huaweiMediaPort);
            }
            if (signalPortChanged) {
                sendEmpty(signalSocket, fccServer, fccServerPort);
            }
            startUnicastWait("server accepted request");
        } else if (c.actionType == 3) {
            redirectCount++;
            if (redirectCount > 5) {
                fallbackToMulticast("too many redirects");
                return;
            }
            log.d("FCC (Telecom): redirect #" + redirectCount + " to "
                    + fccServer.getHostAddress() + ":" + fccServerPort);
            setState(State.INIT, "server redirect");
            sendRequest();
            setState(State.REQUESTED, "request resent");
        } else {
            fallbackToMulticast("unsupported type " + c.actionType);
        }
    }

    private void handleHuaweiResponse(FccControl c) {
        if (state != State.REQUESTED) return;

        if (c.resultCode != 1) {
            fallbackToMulticast("server error code " + c.resultCode);
            return;
        }

        if (c.actionType == 1) {
            fallbackToMulticast("no unicast needed");
            return;
        }

        if (c.actionType == 2) {
            log.d("FCC (Huawei): first_seq=" + c.firstSeq + " bitrate=" + c.bitrateKbps
                    + "Kbps nat=" + c.natFlag + " session=0x" + Long.toHexString(c.sessionId));
            if (c.natServerIp != null) {
                try {
                    fccServer = InetAddress.getByName(c.natServerIp);
                    verifyServerIp = true;
                } catch (Exception ignored) {
                }
            }
            if (c.natServerPort != 0) {
                huaweiMediaPort = c.natServerPort;
            }
            if (c.natFlag == 1 && c.sessionId != 0) {
                needNatTraversal = true;
                sessionId = c.sessionId;
                try {
                    DatagramSocket socket = mediaSocket != null ? mediaSocket : signalSocket;
                    int port = huaweiMediaPort != 0 ? huaweiMediaPort : fccServerPort;
                    sendTriple(socket, FccPackets.buildHuaweiNat(sessionId), fccServer, port);
                    log.d("FCC (Huawei): NAT traversal packet sent");
                } catch (Exception e) {
                    log.w("FCC (Huawei): NAT packet failed: " + e);
                }
            }
            startUnicastWait("server accepted request");
            return;
        }

        if (c.actionType == 3) {
            redirectCount++;
            if (redirectCount > 5) {
                fallbackToMulticast("too many redirects");
                return;
            }
            if (c.natServerIp != null) {
                try {
                    fccServer = InetAddress.getByName(c.natServerIp);
                    verifyServerIp = true;
                } catch (Exception ignored) {
                }
            }
            if (c.natServerPort != 0) {
                fccServerPort = c.natServerPort;
            }
            log.d("FCC (Huawei): redirect #" + redirectCount + " to "
                    + fccServer.getHostAddress() + ":" + fccServerPort);
            setState(State.INIT, "server redirect");
            sendRequest();
            setState(State.REQUESTED, "request resent");
            return;
        }

        fallbackToMulticast("unsupported type " + c.actionType);
    }

    private void startUnicastWait(String reason) {
        unicastStartTime = System.currentTimeMillis();
        setState(State.UNICAST_PENDING, reason);
    }

    void onSyncNotification(String reason) {
        if (state == State.MCAST_REQUESTED || state == State.MCAST_ACTIVE) return;
        log.d("FCC: " + reason + ", joining multicast");
        setState(State.MCAST_REQUESTED, reason);
        joinMulticast();
    }

    void fallbackToMulticast(String reason) {
        if (state == State.MCAST_ACTIVE) return;
        log.w("FCC: " + reason + ", falling back to multicast");
        setState(State.MCAST_ACTIVE, reason);
        switched = true;
        flushPendingMcast();
        joinMulticast();
        if (listener != null) listener.onFccFallback(reason);
    }

    // ------------------------------------------------------------------
    // Media handling
    // ------------------------------------------------------------------

    void handleUnicast(RtpPacket packet) {
        if (switched) return;
        sawUnicastData = true;
        if (state == State.UNICAST_PENDING) {
            setState(State.UNICAST_ACTIVE, "first unicast packet");
            unicastStartTime = System.currentTimeMillis();
            log.d("FCC: Unicast stream started successfully");
            if (listener != null) listener.onFccActive();
        }
        if (state == State.ERROR) return;

        if (!packet.rtp) {
            // Bare MPEG-TS over UDP: no sequence numbers, pass through.
            writePayload(packet);
            return;
        }

        unicastReorder.insert(packet, unicastDeliver);

        if (firstMcastSeq >= 0 && state != State.MCAST_ACTIVE && unicastReorder.phase() == 2) {
            int last = unicastReorder.lastDeliveredSeq();
            if (last >= 0 && (short) (last - (firstMcastSeq - 1)) >= 0) {
                log.d("FCC: Switching to multicast stream (reached termination sequence)");
                switched = true;
                setState(State.MCAST_ACTIVE, "reached termination sequence");
                flushPendingMcast();
            }
        }
    }

    void handleMulticast(RtpPacket packet) {
        if (!packet.rtp) {
            if (switched) {
                writePayload(packet);
            } else {
                bufferPendingMcast(packet);
            }
            return;
        }
        if (state == State.MCAST_REQUESTED && firstMcastSeq < 0) {
            firstMcastSeq = packet.seq;
            termSeqs = (firstMcastSeq + 2) & 0xFFFF;
            sendTermination(termSeqs);
        }
        multicastReorder.insert(packet, multicastDeliver);
    }

    private void bufferPendingMcast(RtpPacket packet) {
        pendingMcast.add(packet);
        pendingMcastBytes += packet.length;
        while (pendingMcastBytes > MAX_PENDING_MCAST_BYTES && pendingMcast.size() > 1) {
            RtpPacket removed = pendingMcast.remove(0);
            pendingMcastBytes -= removed.length;
        }
    }

    private void flushPendingMcast() {
        if (pendingMcast.isEmpty()) return;
        for (int i = 0; i < pendingMcast.size(); i++) {
            writePayload(pendingMcast.get(i));
        }
        log.d("FCC: flushed " + pendingMcast.size() + " buffered multicast packets");
        pendingMcast.clear();
        pendingMcastBytes = 0;
    }

    private void writePayload(RtpPacket packet) {
        sink.write(packet.payload, packet.offset, packet.length);
    }

    // ------------------------------------------------------------------
    // Timeouts
    // ------------------------------------------------------------------

    void tick(long now) {
        if (state == State.REQUESTED || state == State.UNICAST_PENDING) {
            if (now - lastFccDataTime >= SIGNALING_TIMEOUT_MS) {
                fallbackToMulticast("Server response timeout (" + SIGNALING_TIMEOUT_MS + " ms)");
            }
        }

        if (state == State.UNICAST_ACTIVE || state == State.MCAST_REQUESTED) {
            if (now - lastFccDataTime >= UNICAST_TIMEOUT_MS) {
                fallbackToMulticast("unicast stream interrupted");
            } else if (state == State.UNICAST_ACTIVE && unicastStartTime > 0
                    && now - unicastStartTime >= SYNC_WAIT_TIMEOUT_MS) {
                onSyncNotification("sync notification timeout");
            }
        }

        boolean hasUnicast = sawUnicastData || unicastReorder.phase() > 0;
        boolean hasMulticast = lastMcastDataTime > 0;
        if (!hasUnicast && !hasMulticast && now - startTime >= TOTAL_START_TIMEOUT_MS) {
            fatal("timeout waiting for FCC/multicast stream");
            return;
        }
        if (mcastJoined && !hasMulticast && now - mcastJoinTime >= MCAST_FIRST_PACKET_TIMEOUT_MS) {
            fatal("multicast stream join timeout");
            return;
        }
        if (hasMulticast && now - lastMcastDataTime >= MCAST_STALL_TIMEOUT_MS) {
            fatal("multicast stream stalled");
        }
    }

    void setState(State newState, String reason) {
        State old = state;
        state = newState;
        log.d("FCC State: " + old + " -> " + newState + " (" + reason + ")");
    }

    private void fatal(String reason) {
        log.e("FCC fatal: " + reason);
        setState(State.ERROR, reason);
        running = false;
        if (listener != null) listener.onFatal(reason);
    }

    private void joinMulticast() {
        if (mcastJoined || multicastSocket != null) return;
        if (multicastAddress == null) {
            log.e("FCC: multicast address not resolved");
            return;
        }
        try {
            MulticastSocket socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            try {
                socket.setReceiveBufferSize(SOCKET_RECEIVE_BUFFER);
            } catch (Exception ignored) {
            }
            socket.bind(new InetSocketAddress(channel.multicastPort));
            if (networkInterface != null) {
                try {
                    socket.setNetworkInterface(networkInterface);
                } catch (Exception e) {
                    log.w("FCC: setNetworkInterface failed: " + e);
                }
            }
            InetSocketAddress group = new InetSocketAddress(multicastAddress, channel.multicastPort);
            if (networkInterface != null) {
                socket.joinGroup(group, networkInterface);
            } else {
                socket.joinGroup(multicastAddress);
            }
            multicastSocket = socket;
            mcastJoined = true;
            mcastJoinTime = System.currentTimeMillis();
            log.d("FCC: joined multicast " + channel.multicastIp + ":" + channel.multicastPort);
            startWorker(socket, false);
        } catch (Exception e) {
            log.e("FCC: multicast join failed: " + e);
        }
    }

    private void leaveMulticast() {
        MulticastSocket socket = multicastSocket;
        multicastSocket = null;
        if (socket == null) return;
        try {
            InetSocketAddress group = new InetSocketAddress(multicastAddress, channel.multicastPort);
            if (networkInterface != null) {
                socket.leaveGroup(group, networkInterface);
            } else {
                socket.leaveGroup(multicastAddress);
            }
        } catch (Exception ignored) {
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private void closeSocketsQuietly() {
        try {
            if (mediaSocket != null && mediaSocket != signalSocket) mediaSocket.close();
        } catch (Exception ignored) {
        }
        try {
            if (signalSocket != null) signalSocket.close();
        } catch (Exception ignored) {
        }
        try {
            if (multicastSocket != null) multicastSocket.close();
        } catch (Exception ignored) {
        }
    }

    private void cleanup() {
        synchronized (packetLock) {
            try {
                if (!termSent) {
                    sendTermination(0);
                }
            } catch (Exception ignored) {
            }
            leaveMulticast();
            closeSocketsQuietly();
            mediaSocket = null;
            signalSocket = null;
        }
    }
}
