package com.github.tvbox.osc.fcc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded blocking byte queue bridging the FCC/multicast receiver thread and
 * the local HTTP relay thread.
 *
 * <p>When the consumer is slower than the incoming stream the oldest data is
 * dropped so live playback stays close to real time instead of growing an
 * unbounded buffer.
 */
public final class ByteQueue {

    private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
    private final long maxBytes;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private long totalBytes;
    private long droppedBytes;
    private boolean closed;

    public ByteQueue(long maxBytes) {
        this.maxBytes = Math.max(maxBytes, 64 * 1024);
    }

    public void write(byte[] data, int offset, int length) {
        if (data == null || length <= 0) return;
        byte[] chunk;
        if (offset == 0 && length == data.length) {
            chunk = data;
        } else {
            chunk = new byte[length];
            System.arraycopy(data, offset, chunk, 0, length);
        }
        lock.lock();
        try {
            if (closed) return;
            chunks.addLast(chunk);
            totalBytes += chunk.length;
            while (totalBytes > maxBytes && chunks.size() > 1) {
                byte[] removed = chunks.removeFirst();
                totalBytes -= removed.length;
                droppedBytes += removed.length;
            }
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Blocking read. Returns -1 once the queue is closed and drained. */
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return read(buffer, offset, length, 0);
    }

    /**
     * Read with an optional timeout while waiting for the first byte.
     *
     * @param timeoutMs 0 = wait forever, otherwise return -1 when no data
     *                  arrived within the timeout
     */
    public int read(byte[] buffer, int offset, int length, long timeoutMs) throws IOException {
        if (buffer == null) throw new IOException("null buffer");
        if (length == 0) return 0;
        lock.lock();
        try {
            long remainingNanos = timeoutMs > 0 ? timeoutMs * 1000000L : 0;
            while (totalBytes == 0 && !closed) {
                if (timeoutMs <= 0) {
                    notEmpty.awaitUninterruptibly();
                } else {
                    if (remainingNanos <= 0) return -1;
                    try {
                        remainingNanos = notEmpty.awaitNanos(remainingNanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return -1;
                    }
                }
            }
            if (totalBytes == 0) return -1;
            int written = 0;
            while (written < length) {
                byte[] head = chunks.peekFirst();
                if (head == null) break;
                int available = head.length;
                int chunkOffset = 0;
                if (available <= 0) {
                    chunks.removeFirst();
                    continue;
                }
                int toCopy = Math.min(available, length - written);
                System.arraycopy(head, chunkOffset, buffer, offset + written, toCopy);
                written += toCopy;
                if (toCopy == available) {
                    chunks.removeFirst();
                    totalBytes -= available;
                } else {
                    byte[] rest = new byte[available - toCopy];
                    System.arraycopy(head, toCopy, rest, 0, rest.length);
                    chunks.removeFirst();
                    chunks.addFirst(rest);
                    totalBytes -= toCopy;
                }
            }
            return written > 0 ? written : -1;
        } finally {
            lock.unlock();
        }
    }

    public InputStream getInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = ByteQueue.this.read(one, 0, 1);
                return n == 1 ? one[0] & 0xFF : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return ByteQueue.this.read(b, off, len);
            }
        };
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    public long droppedBytes() {
        lock.lock();
        try {
            return droppedBytes;
        } finally {
            lock.unlock();
        }
    }
}
