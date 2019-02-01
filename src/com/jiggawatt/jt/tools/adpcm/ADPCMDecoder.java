package com.jiggawatt.jt.tools.adpcm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * Decodes ADPCM input to 16-bit PCM data.
 * @author Nikita Leonidov
 */
public final class ADPCMDecoder {

    private final ADPCMDecoderConfig config;

    private final int numChannels;
    private final int blockSize;
    private final int samplesPerBlock;

    private final short[] pcmBlock;
    private final byte[]  adpcmBlock;

    public ADPCMDecoder(ADPCMDecoderConfig cfg) {
        config          = cfg;
        numChannels     = cfg.getChannels();
        blockSize       = cfg.getBlockSize();
        samplesPerBlock = cfg.getSamplesPerBlock();

        pcmBlock        = new short[samplesPerBlock * numChannels];
        adpcmBlock      = new byte[blockSize];
    }

    /**
     * Creates a configuration object builder.
     * @return a configuration builder with default parameters
     */
    public static ADPCMDecoderConfig.Builder configure() {
        return new ADPCMDecoderConfig.Builder(null);
    }

    /**
     * Creates a builder for a copy of an existing configuration object.
     * @param other a configuration object from which to initialize the builder
     * @return a builder with the same parameters as the given configuration object
     */
    public static ADPCMDecoderConfig.Builder configure(ADPCMDecoderConfig other) {
        return new ADPCMDecoderConfig.Builder(other);
    }

    /**
     * @return this decoder's configuration object
     */
    public ADPCMDecoderConfig getConfiguration() {
        return config;
    }

    /**
     * Decodes <tt>out.{@link ShortBuffer#remaining() remaining()}/{@link #getConfiguration()}.{@link
     * ADPCMDecoderConfig#getChannels() getChannels()}</tt> samples from <tt>in</tt>.
     * @param in  input buffer; contains ADPCM data
     * @param out output buffer for 16-bit PCM data
     * @return <tt>out</tt>
     * @throws IOException when an encoding problem occurs
     */
    public ShortBuffer decode(ByteBuffer in, ShortBuffer out) throws IOException {
        while (in.hasRemaining()) {
            int blockAdpcmSamples = samplesPerBlock;
            int blockPcmSamples   = samplesPerBlock;
            int currentBlockSize  = blockSize;

            int numSamples = out.remaining()/numChannels;
            if (blockAdpcmSamples > numSamples) {
                blockAdpcmSamples = ((numSamples + 6) & ~7) + 1;
                currentBlockSize  = (blockAdpcmSamples - 1) / (numChannels ^ 3) + (numChannels * 4);
                blockPcmSamples   = numSamples;
            }

            in.get(adpcmBlock, 0, currentBlockSize);

            decodeBlock(pcmBlock, adpcmBlock, currentBlockSize);

            out.put(pcmBlock, 0, blockPcmSamples * numChannels);
        }

        return out;
    }

    private void decodeBlock(short[] outBuf, byte[] inBuf, int inBufSize) throws IOException {
        int[]  pcmData = new int[2];
        byte[] index   = new byte[2];
        int    outPtr  = 0;
        int    inPtr   = 0;

        if (inBufSize < numChannels*4) {
            throw new IOException("too few elements left in input buffer");
        }

        for (int ch=0; ch<numChannels; ch++) {
            int a = Byte.toUnsignedInt(inBuf[inPtr]);
            int b = Byte.toUnsignedInt(inBuf[inPtr+1]);

            pcmData[ch] = outBuf[outPtr++] = (short)(a | (b << 8));
            index[ch] = inBuf[inPtr+2];

            if (index[ch] < 0 || index[ch] > 88 || inBuf[inPtr+3]!=0) {
                throw new IOException("malformed block header");
            }

            inBufSize -= 4;
            inPtr     += 4;
        }

        int chunks = inBufSize / (numChannels*4);

        while ((chunks--)> 0) {
            for (int ch=0; ch<numChannels; ch++) {
                for (int i=0; i<4; i++) {
                    int step = ADPCM.stepTable(index[ch]);

                    int delta = step >> 3;
                    int v = Byte.toUnsignedInt(inBuf[inPtr]);

                    if ((v & 1) != 0) delta += (step >> 2);
                    if ((v & 2) != 0) delta += (step >> 1);
                    if ((v & 4) != 0) delta += step;
                    if ((v & 8) != 0) delta = -delta;

                    pcmData[ch] += delta;
                    index  [ch] += ADPCM.indexTable(v & 0x7);
                    index  [ch]  = clip(index[ch], 0, 88);
                    pcmData[ch]  = clip(pcmData[ch], -32768, 32767);
                    outBuf[outPtr + i*2*numChannels] = (short)pcmData[ch];

                    step = ADPCM.stepTable(index[ch]);
                    delta = step >> 3;

                    if ((v & 0x10) != 0) delta += (step >> 2);
                    if ((v & 0x20) != 0) delta += (step >> 1);
                    if ((v & 0x40) != 0) delta += step;
                    if ((v & 0x80) != 0) delta = -delta;

                    pcmData[ch] += delta;
                    index  [ch] += ADPCM.indexTable((v >> 4) &0x7);
                    index  [ch]  = clip(index[ch], 0, 88);
                    pcmData[ch]  = clip(pcmData[ch], -32768, 32767);
                    outBuf[outPtr + (i*2+1)*numChannels] = (short) pcmData[ch];

                    inPtr++;
                }

                outPtr++;
            }

            outPtr += numChannels * 7;
        }
    }

    private static int clip(int in, int min, int max) {
        if (in>max) {
            return max;
        }
        if (in<min) {
            return min;
        }
        return in;
    }

    private static byte clip(byte in, int min, int max) {
        if (in>max) {
            return (byte)max;
        }
        if (in<min) {
            return (byte)min;
        }
        return in;
    }
}
