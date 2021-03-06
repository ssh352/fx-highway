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
package org.tools4j.fx.highway.chronicle;

import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.HdrHistogram.Histogram;
import org.agrona.concurrent.NanoClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.octtech.bw.ByteWatcher;
import org.tools4j.fx.highway.util.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(Parameterized.class)
//@Ignore("too slow to run")
public class ChronicleQueue4xRawDataLatencyTest {

    private final long messagesPerSecond;
    private final int numberOfBytes;
    private final boolean affinity;

    private ChronicleQueue4x chronicleQueue;
    private ByteWatcher byteWatcher;

    @Parameterized.Parameters(name = "{index}: MPS={0}, NBYTES={1}, AFFINITY={2}")
    public static Collection testRunParameters() {
        return Arrays.asList(new Object[][] {
                { 160000, 100, false },
                { 500000, 100, false },
//                { 160000, 100, true },
//                { 500000, 100, true }
        });
    }

    public ChronicleQueue4xRawDataLatencyTest(final long messagesPerSecond,
                                              final int numberOfBytes,
                                              final boolean affinity) {
        this.messagesPerSecond = messagesPerSecond;
        this.numberOfBytes = numberOfBytes;
        this.affinity = affinity;
    }

    @Before
    public void setup() throws Exception {
        chronicleQueue = new ChronicleQueue4x();
        byteWatcher = ByteWatcherPrinter.watch();
    }

    @After
    public void tearDown() throws Exception {
        if (chronicleQueue != null) {
            chronicleQueue.close();
            chronicleQueue = null;
        }
        if (byteWatcher != null) {
            byteWatcher.shutdown();
            byteWatcher = null;
        }
    }

    @Test
    public void latencyTest() throws Exception {
        //given
        final long histogramMax = TimeUnit.SECONDS.toNanos(1);
        final int w = 200000;//warmup
        final int c = 100000;//counted
        final int n = w+c;
        final long maxTimeToRunSeconds = 30;

        System.out.println("\twarmup + count      : " + w + " + " + c + " = " + n);
        System.out.println("\tmessagesPerSecond   : " + messagesPerSecond);
        System.out.println("\tmessageSize         : " + numberOfBytes + " bytes");
        System.out.println("\tmaxTimeToRunSeconds : " + maxTimeToRunSeconds);
        System.out.println();

        final AtomicBoolean terminate = new AtomicBoolean(false);
        final NanoClock clock = SerializerHelper.NANO_CLOCK;
        final Histogram histogram = new Histogram(1, histogramMax, 3);
        final WaitLatch pubSubReadyLatch = new WaitLatch(2);
        final WaitLatch receivedAllLatch = new WaitLatch(1);
        final AtomicInteger count = new AtomicInteger();

        //when
        final Thread subscriberThread = new AffinityThread(affinity, () -> {
            final ExcerptTailer tailer = chronicleQueue.getTailer();
            final AtomicLong t0 = new AtomicLong();
            final AtomicLong t1 = new AtomicLong();
            final AtomicLong t2 = new AtomicLong();
            final ReadMarshallable rm = m -> {
                if (count.get() == 0) t0.set(clock.nanoTime());
                else if (count.get() == w - 1) t1.set(clock.nanoTime());
                else if (count.get() == n - 1) t2.set(clock.nanoTime());
                long sendTime = m.getValueIn().int64();
                long raw = 0;
                for (int i = 8; i < numberOfBytes; ) {
                    if (i + 8 <= numberOfBytes) {
                        raw += m.getValueIn().int64();
                        i += 8;
                    } else {
                        raw += m.getValueIn().int8();
                        i++;
                    }
                }
                final long time = clock.nanoTime();
                final int cnt = count.incrementAndGet();
                if (cnt <= n && raw != 0) {
                    if (time - sendTime > histogramMax) {
                        //throw new RuntimeException("bad data in message " + cnt + ": time=" + time + ", sendTime=" + sendTime + ", dt=" + (time - sendTime));
                        histogram.recordValue(histogramMax);
                    } else {
                        histogram.recordValue(time - sendTime);
                    }
                }
                if (cnt == w) {
                    histogram.reset();
                }
            };
            pubSubReadyLatch.countDown();
            while (!terminate.get()) {
                if (tailer.readDocument(rm)) {
                    if (count.get() >= n) {
                        receivedAllLatch.countDown();
                        break;
                    }
                }
                ;
            }
            System.out.println((t2.get() - t0.get()) / 1000.0 + " us total receiving time (" + (t2.get() - t1.get()) / (1000f * c) + " us/message, " + c / ((t2.get() - t1.get()) / 1000000000f) + " messages/second)");
        });
        subscriberThread.setName("subscriber-thread");
        subscriberThread.start();

        //publisher
        final Thread publisherThread = new AffinityThread(affinity, () -> {
            final ExcerptAppender appender = chronicleQueue.getAppender();
            final long periodNs = 1000000000/messagesPerSecond;
            final WriteMarshallable wm = m -> {
                final long time = clock.nanoTime();
                m.getValueOut().int64(time);
                for (int i = 8; i < numberOfBytes; ) {
                    if (i + 8 <= numberOfBytes) {
                        m.getValueOut().int64(time + i);
                        i += 8;
                    } else {
                        m.getValueOut().int8((byte) (time + i));
                        i++;
                    }
                }
            };
            pubSubReadyLatch.countDown();
            pubSubReadyLatch.awaitThrowOnTimeout(5, TimeUnit.SECONDS);
            long cntAdmin = 0;
            long cntBackp = 0;
            long cnt = 0;
            final long t0 = clock.nanoTime();
            while (cnt < n && !terminate.get()) {
                long tCur = clock.nanoTime();
                while (tCur - t0 < cnt * periodNs) {
                    tCur = clock.nanoTime();
                }
                appender.writeDocument(wm);
                cnt++;
            }
            final long t1 = clock.nanoTime();
            System.out.println((t1 - t0) / 1000.0 + " us total publishing time (backp=" + cntBackp + ", admin=" + cntAdmin + ", cnt=" + cnt + ")");
        });
        publisherThread.setName("publisher-thread");
        publisherThread.start();;

        //then
        if (!receivedAllLatch.await(maxTimeToRunSeconds, TimeUnit.SECONDS)) {
            terminate.set(true);
            System.err.println("timeout after receiving " + count + " messages.");
            throw new RuntimeException("simulation timed out");
        }
        terminate.set(true);

        publisherThread.join(2000);

        System.out.println();
        HistogramPrinter.printHistogram(histogram);
    }

    public static void main(String... args) throws Exception {
        final int messageSize = 100;
        final int[] messagesPerSecond = { 160000, 160000, 500000, 1000000 };
        for (final int mps : messagesPerSecond) {
            final ChronicleQueue4xRawDataLatencyTest chronicleQueueLatencyTest = new ChronicleQueue4xRawDataLatencyTest(mps, messageSize, false);
            chronicleQueueLatencyTest.setup();
            try {
                chronicleQueueLatencyTest.latencyTest();
            } finally {
                chronicleQueueLatencyTest.tearDown();
            }
        }
    }
}
