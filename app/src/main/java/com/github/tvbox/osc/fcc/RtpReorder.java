package com.github.tvbox.osc.fcc;

/**
 * Small RTP reorder buffer, ported from rtp2httpd's {@code rtp_reorder.c}
 * without the FEC parts.
 *
 * <p>Phase 0/1 collect a few initial packets before the base sequence is
 * pinned (Huawei switches may deliver the first packets out of order, which
 * would otherwise corrupt the TS stream at startup). Phase 2 delivers in
 * order, drops late/duplicate packets and force-flushes when the window is
 * exceeded so the stream never stalls permanently on a lost packet.
 */
public final class RtpReorder {
    private static final int WINDOW_SIZE = 64;
    private static final int WINDOW_MASK = WINDOW_SIZE - 1;
    private static final int INIT_COLLECT = 8;

    public interface Deliver {
        void deliver(RtpPacket packet);
    }

    private final RtpPacket[] slots = new RtpPacket[WINDOW_SIZE];
    private final int[] slotSeq = new int[WINDOW_SIZE];
    private int baseSeq;
    private int count;
    private int phase; // 0 = not started, 1 = collecting, 2 = active

    /**
     * Insert a packet and deliver whatever became deliverable.
     *
     * @return number of bytes delivered
     */
    public int insert(RtpPacket packet, Deliver deliver) {
        int seqn = packet.seq & 0xFFFF;
        int delivered = 0;

        if (phase == 0) {
            baseSeq = seqn;
            phase = 1;
            int slot = seqn & WINDOW_MASK;
            slots[slot] = packet;
            slotSeq[slot] = seqn;
            count = 1;
            return 0;
        }

        if (phase == 1) {
            int slot = seqn & WINDOW_MASK;
            if (slots[slot] == null) {
                slots[slot] = packet;
                slotSeq[slot] = seqn;
                count++;
                if ((short) (seqn - baseSeq) < 0) {
                    baseSeq = seqn;
                }
            }
            if (count >= INIT_COLLECT) {
                phase = 2;
                delivered += flushConsecutive(deliver);
            }
            return delivered;
        }

        int diff = (short) (seqn - baseSeq);

        if (diff == 0) {
            int slot = seqn & WINDOW_MASK;
            if (count == 0 && slots[slot] == null) {
                deliver.deliver(packet);
                baseSeq = (baseSeq + 1) & 0xFFFF;
                return packet.length;
            }
            slots[slot] = packet;
            slotSeq[slot] = seqn;
            count++;
            return flushConsecutive(deliver);
        }

        if (diff < 0) {
            return 0; // late / duplicate
        }

        if (diff >= WINDOW_SIZE) {
            delivered += forceFlushUntil(seqn, deliver);
        }

        int slot = seqn & WINDOW_MASK;
        if (slots[slot] != null) {
            if (slotSeq[slot] == seqn) {
                return delivered; // duplicate
            }
            slots[slot] = null;
        }
        slots[slot] = packet;
        slotSeq[slot] = seqn;
        count++;
        return delivered;
    }

    private int flushConsecutive(Deliver deliver) {
        int delivered = 0;
        while (count > 0) {
            int slot = baseSeq & WINDOW_MASK;
            RtpPacket packet = slots[slot];
            if (packet == null) break;
            deliver.deliver(packet);
            delivered += packet.length;
            slots[slot] = null;
            baseSeq = (baseSeq + 1) & 0xFFFF;
            count--;
        }
        return delivered;
    }

    private int forceFlushUntil(int targetSeq, Deliver deliver) {
        int delivered = 0;
        while ((short) (targetSeq - baseSeq) >= WINDOW_SIZE) {
            int slot = baseSeq & WINDOW_MASK;
            RtpPacket packet = slots[slot];
            if (packet != null) {
                deliver.deliver(packet);
                delivered += packet.length;
                slots[slot] = null;
                count--;
            }
            baseSeq = (baseSeq + 1) & 0xFFFF;
        }
        return delivered;
    }

    /** Last sequence number delivered, or -1 before anything was delivered. */
    public int lastDeliveredSeq() {
        if (phase != 2) return -1;
        return (baseSeq - 1) & 0xFFFF;
    }

    public int phase() {
        return phase;
    }
}
