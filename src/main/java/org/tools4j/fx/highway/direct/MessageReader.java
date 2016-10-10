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

/**
 * Message writer offers methods to write different value types for elements of a message.
 */
public interface MessageReader {
    boolean getBoolean();
    byte getInt8();
    int getInt8AsInt();
    short getInt16();
    int getInt16AsInt();
    int getInt32();
    long getInt64();
    float getFloat32();
    double getFloat64();
    char getChar();
    char getCharAscii();
    CharSequence getStringAscii();
    /**
     * Reads in a representation of a Unicode character string encoded in
     * <a href="DataInput.html#modified-utf-8">modified UTF-8</a> format;
     * this string of characters is then returned as a <code>CharSequence</code>.
     * The details of the modified UTF-8 representation
     * are  exactly the same as for the <code>readUTF</code>
     * method of <code>DataInput</code>.
     *
     * @return     a Unicode string.
     * @see        DataInputStream#readUTF(DataInput)
     */
    CharSequence getStringUtf8();
    CharSequence getString();
    Sequencer finishReadMessage();
}
