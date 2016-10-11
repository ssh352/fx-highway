/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 fx-highway (tools4j), Marco Terzer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.fx.highway.direct;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by terz on 9/10/2016.
 */
public class MappedFile implements Closeable {

    //must be power of 2!
    public static final long REGION_SIZE_MULTIPLE = 8;

    public enum Mode {
        READ_ONLY("r"),
        READ_WRITE("rw"),
        /** Delete file contents on open*/
        READ_WRITE_CLEAR("rw");

        private final String rasMode;
        Mode(final String rasMode) {
            this.rasMode = Objects.requireNonNull(rasMode);
        }

        public String getRandomAccessMode() {
            return rasMode;
        }
    }

    public interface FileInitialiser {
        void init(FileChannel file, Mode mode) throws IOException;
    }


    private final RandomAccessFile file;
    private final Mode mode;
    private final long regionSize;

    private volatile AtomicReferenceArray<MappedRegion> mappedRegions = new AtomicReferenceArray<MappedRegion>(2);

    public MappedFile(final String fileName, final Mode mode, final long regionSize) throws IOException {
        this(new File(fileName), mode, regionSize);
    }

    public MappedFile(final String fileName, final Mode mode, final long regionSize, final FileInitialiser fileInitialiser) throws IOException {
        this(new File(fileName), mode, regionSize, fileInitialiser);
    }

    public MappedFile(final File file, final Mode mode, final long regionSize) throws IOException {
        this(file, mode, regionSize, MappedFile::initFile);
    }

    public MappedFile(final File file, final Mode mode, final long regionSize, final FileInitialiser fileInitialiser) throws IOException {
        if (regionSize <= 0 || (regionSize & (REGION_SIZE_MULTIPLE-1)) != 0) {
            throw new IllegalArgumentException("Region size must be positive and a multiple of " + REGION_SIZE_MULTIPLE + " but was " + regionSize);
        }
        if (!file.exists()) {
            if (mode == Mode.READ_ONLY) {
                throw new FileNotFoundException(file.getAbsolutePath());
            }
            file.createNewFile();
        }
        final RandomAccessFile raf = new RandomAccessFile(file, mode.getRandomAccessMode());
        this.file = Objects.requireNonNull(raf);
        this.mode = Objects.requireNonNull(mode);
        this.regionSize = regionSize;
        fileInitialiser.init(raf.getChannel(), mode);
    }

    private static void initFile(final FileChannel fileChannel, final MappedFile.Mode mode) throws IOException {
        if (mode == Mode.READ_WRITE_CLEAR) {
            final FileLock lock = fileChannel.lock();
            try {
                fileChannel.truncate(0);
                fileChannel.force(true);
            } finally {
                lock.release();
            }
        }
    }

    public Mode getMode() {
        return mode;
    }

    public long getRegionSize() {
        return regionSize;
    }

    public long getFileLength() {
        ensureNotClosed();
        try {
            return file.length();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFileLength(final long length) {
        ensureNotClosed();
        try {
            file.setLength(length);
        } catch (final IOException e) {
            throw new RuntimeException("could not set file length to " + length, e);
        }
    }

    public int getRegionIndexForPosition(final long position) {
        final long index = position / regionSize;
        return index <= Integer.MAX_VALUE ? (int)index : -1;
    }

    public void releaseRegion(final MappedRegion mappedRegion) {
        ensureNotClosed();
        if (0 == mappedRegion.decAndGetRefCount()) {
            final long index = mappedRegion.getPosition() / regionSize;
            if (index < mappedRegions.length()) {
                final int ix = (int) index;
                final MappedRegion mr = mappedRegions.get(ix);
                if (mr != null && mr.isClosed()) {
                    mappedRegions.compareAndSet(ix, mr, null);
                }
            }
        }
    }

    public MappedRegion reserveRegion(final int index) {
        ensureNotClosed();
        ensureSufficientMappedRegionsCapacity(index);
        //ASSERT: index < mappedRegions.length
        final MappedRegion mr = mappedRegions.get(index);
        if (mr == null || mr.isClosed() || mr.incAndGetRefCount() == 0) {
            final long position = index * regionSize;
            final long minLen = position + regionSize;
            final long curLen = getFileLength();
            if (curLen < minLen) {
                setFileLength(minLen);
            }
            final MappedRegion newRegion = new MappedRegion(file.getChannel(), index, position, regionSize);
            if (mappedRegions.compareAndSet(index, mr, newRegion)) {
                return newRegion;
            }
            //region has been created by someone else
            newRegion.decAndGetRefCount();
            return reserveRegion(index);
        }
        //region exists and ref count increment was successful
        return mr;
    }

    private void ensureSufficientMappedRegionsCapacity(final int index) {
        if (index < mappedRegions.length()) {
            return;
        }
        if (index < 0 || index > Integer.MAX_VALUE - 1) {
            throw new IllegalArgumentException("index out of bounds: " + index);
        }
        synchronized (this) {
            final AtomicReferenceArray<MappedRegion> oldArr = mappedRegions;
            final int oldLen = mappedRegions.length();
            if (index < oldLen) {
                return;
            }
            final int newLen = Math.max(index + 1, oldLen * 2);//overflow would be corrected by max
            final AtomicReferenceArray<MappedRegion> newArr = new AtomicReferenceArray<>(newLen);
            for (int i = 0; i < oldLen; i++) {
                final MappedRegion mr = oldArr.get(i);
                if (mr != null && !mr.isClosed()) {
                    newArr.set(i, mr);
                }
            }
            mappedRegions = newArr;
        }
    }

    private void ensureNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Mapped file is closed");
        }
    }

    public boolean isClosed() {
        return mappedRegions == null;
    }

    public void close() {
        if (mappedRegions == null) {
            return;
        }
        try {
            final AtomicReferenceArray<MappedRegion> arr;
            synchronized (this) {
                arr = mappedRegions;
                mappedRegions = null;
            }
            if (arr == null) {
                return;
            }
            final int len = arr.length();
            for (int i = 0; i < len; i++) {
                final MappedRegion mr = arr.get(i);
                if (mr != null) {
                    if (!mr.isClosed()) {
                        throw new IllegalStateException("Not all mapped regions are closed, close appender and all sequencers first");
                    }
                    if (!arr.compareAndSet(i, mr, null)) {
                        throw new IllegalStateException("Appender or sequencers seem still active, close appender and all sequencers first");
                    }
                }
            }
            file.getChannel().close();
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
