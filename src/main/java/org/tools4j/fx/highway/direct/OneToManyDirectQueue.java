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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MappedQueue implementation optimised for single Appender and multiple Enumerator support.
 */
public class OneToManyDirectQueue implements MappedQueue {

    public static final long DEFAULT_REGION_SIZE = 4L << 20;//4 MB

    private final MappedFile file;
    private final AtomicBoolean appenderCreated = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private OneToManyDirectQueue(final MappedFile file) {
        this.file = Objects.requireNonNull(file);
    }

    public static final MappedQueue createOrReplace(final String fileName) throws IOException {
        return createOrReplace(fileName, DEFAULT_REGION_SIZE);
    }

    public static final MappedQueue createOrReplace(final String fileName, final long regionSize) throws IOException {
        return open(new MappedFile(fileName, MappedFile.Mode.READ_WRITE_CLEAR, regionSize, OneToManyDirectQueue::initFile));
    }

    public static final MappedQueue createOrAppend(final String fileName) throws IOException {
        return createOrAppend(fileName, DEFAULT_REGION_SIZE);
    }

    public static final MappedQueue createOrAppend(final String fileName, final long regionSize) throws IOException {
        return open(new MappedFile(fileName, MappedFile.Mode.READ_WRITE, regionSize, OneToManyDirectQueue::initFile));
    }

    public static final MappedQueue openReadOnly(final String fileName) throws IOException {
        return openReadOnly(fileName, DEFAULT_REGION_SIZE);
    }

    public static final MappedQueue openReadOnly(final String fileName, final long regionSize) throws IOException {
        return open(new MappedFile(fileName, MappedFile.Mode.READ_ONLY, regionSize));
    }

    public static final MappedQueue open(final MappedFile file) {
        return new OneToManyDirectQueue(file);
    }

    private static void initFile(final FileChannel fileChannel, final MappedFile.Mode mode) throws IOException {
        final FileLock lock = fileChannel.lock();
        try {
            switch (mode) {
                case READ_ONLY:
                    if (fileChannel.size() < 8) {
                        throw new IllegalArgumentException("Invalid file format");
                    }
                    break;
                case READ_WRITE:
                    if (fileChannel.size() >= 8) {
                        break;
                    }
                    //else: FALL THROUGH
                case READ_WRITE_CLEAR:
                    fileChannel.truncate(0);
                    fileChannel.transferFrom(InitialBytes.MINUS_ONE, 0, 8);
                    fileChannel.force(true);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid mode: " + mode);
            }
        } finally {
            lock.release();
        }
    }

    @Override
    public Appender appender() {
        if (file.getMode() == MappedFile.Mode.READ_ONLY) {
            throw new IllegalStateException("Cannot access appender for file in read-only mode");
        }
        if (appenderCreated.compareAndSet(false, true)) {
            return new OneToManyAppender(file);
        }
        throw new IllegalStateException("Only one appender supported");
    }

    @Override
    public Enumerator enumerator() {
        return new OneToManyEnumerator(file);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            file.close();
        }
    }

}
