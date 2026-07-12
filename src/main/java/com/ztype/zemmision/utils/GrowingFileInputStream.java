package com.ztype.zemmision.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.function.Supplier;

public class GrowingFileInputStream extends InputStream {
    private final File file;
    private final Supplier<Boolean> downloadCompleteSupplier;
    private long readOffset = 0;
    private long markOffset = 0;
    private RandomAccessFile raf;

    public GrowingFileInputStream(File file, Supplier<Boolean> downloadCompleteSupplier) throws IOException {
        this.file = file;
        this.downloadCompleteSupplier = downloadCompleteSupplier;
        long start = System.currentTimeMillis();
        while (!file.exists()) {
            if (System.currentTimeMillis() - start > 10000) { // 10s timeout
                throw new java.io.FileNotFoundException("File not created by torrent client: " + file.getAbsolutePath());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for file creation: " + file.getAbsolutePath());
            }
        }
        this.raf = new RandomAccessFile(file, "r");
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        if (n == -1) return -1;
        return b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        while (true) {
            raf.seek(readOffset);
            int n = raf.read(b, off, len);
            if (n != -1) {
                readOffset += n;
                return n;
            }
            
            if (downloadCompleteSupplier.get()) {
                raf.seek(readOffset);
                n = raf.read(b, off, len);
                if (n != -1) {
                    readOffset += n;
                    return n;
                }
                return -1; // EOF
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) return 0;
        long skipped = 0;
        while (skipped < n) {
            long remaining = n - skipped;
            long fileLength = file.length();
            long available = fileLength - readOffset;
            if (available > 0) {
                long toSkip = Math.min(available, remaining);
                readOffset += toSkip;
                skipped += toSkip;
            } else {
                if (downloadCompleteSupplier.get()) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readlimit) {
        markOffset = readOffset;
    }

    @Override
    public synchronized void reset() throws IOException {
        readOffset = markOffset;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
