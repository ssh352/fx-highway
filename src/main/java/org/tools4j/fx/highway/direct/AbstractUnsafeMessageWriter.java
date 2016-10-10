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

import static org.tools4j.fx.highway.direct.DirectUnsafe.UNSAFE;

abstract public class AbstractUnsafeMessageWriter extends AbstractMessageWriter {

    abstract protected long getAndIncrementAddress(final int len);

    @Override
    public MessageWriter putInt8(byte value) {
        UNSAFE.putByte(null, getAndIncrementAddress(1), value);
        return this;
    }

    @Override
    public MessageWriter putInt16(final short value) {
        UNSAFE.putShort(null, getAndIncrementAddress(2), value);
        return this;
    }

    @Override
    public MessageWriter putInt32(final int value) {
        UNSAFE.putInt(null, getAndIncrementAddress(4), value);
        return this;
    }

    @Override
    public MessageWriter putInt64(final long value) {
        UNSAFE.putLong(null, getAndIncrementAddress(8), value);
        return this;
    }

    @Override
    public MessageWriter putFloat32(final float value) {
        UNSAFE.putFloat(null, getAndIncrementAddress(4), value);
        return this;
    }

    @Override
    public MessageWriter putFloat64(final double value) {
        UNSAFE.putDouble(null, getAndIncrementAddress(8), value);
        return this;
    }

    @Override
    public MessageWriter putChar(final char value) {
        UNSAFE.putChar(null, getAndIncrementAddress(2), value);
        return this;
    }

}
