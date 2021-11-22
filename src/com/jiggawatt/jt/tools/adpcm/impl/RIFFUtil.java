package com.jiggawatt.jt.tools.adpcm.impl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class RIFFUtil {

    public static int chunkId(String id) {
        byte[] b = id.getBytes();
        return b[0] | (b[1]<<8) | (b[2]<<16) | (b[3]<<24);
    }

    public static String chunkId(int id) {
        char[] str = new char[]{
            (char)(id & 0xff),
            (char)((id >>>  8) & 0xff),
            (char)((id >>> 16) & 0xff),
            (char)((id >>> 24) & 0xff)
        };
        return new String(str);
    }

    public static void requireId(InputStream in, int expect) throws IOException {
        requireId(intLittleEndian(in), expect);
    }

    public static void requireId(ByteBuffer in, int expect) throws IOException {
        requireId(in.getInt(), expect);
    }

    public static void requireId(int actual, int expect) throws IOException {
        if (actual!=expect) {
            throw new IOException(
                "malformed file; " +
                "expected chunk with identifier "+chunkId(expect)+" ("+Integer.toHexString(expect)+"); " +
                "found "+chunkId(actual)+" ("+Integer.toHexString(actual)+")");
        }
    }

    public static int intLittleEndian(InputStream in) throws IOException {
        return intLittleEndian(
            checkByte(in),
            checkByte(in),
            checkByte(in),
            checkByte(in)
        );
    }

    private static byte checkByte(InputStream in) throws IOException {
        int k = in.read();
        if (k == -1) {
            throw new EOFException();
        }
        return (byte) k;
    }

    public static int intLittleEndian(byte a, byte b, byte c, byte d) {
        return (a & 0xff) | (b & 0xff) << 8 | (c & 0xff) << 16 | d << 24;
    }

    private RIFFUtil() {}
}
