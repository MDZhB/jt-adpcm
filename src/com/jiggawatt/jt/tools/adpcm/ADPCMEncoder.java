package com.jiggawatt.jt.tools.adpcm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/**
 * Encodes 16-bit PCM data to ADPCM.
 * @author Nikita Leonidov
 */
public final class ADPCMEncoder {

    private final ADPCMEncoderConfig config;

    private final int          numChannels;
    private final int          blockSize;
    private final int          samplesPerBlock;
    private final int          lookahead;
    private final NoiseShaping shaping;

    private final short[] pcmBlock;
    private final byte[]  adpcmBlock;

    public ADPCMEncoder(ADPCMEncoderConfig cfg) {
        config          = cfg;
        numChannels     = cfg.getChannels();
        blockSize       = cfg.getBlockSize();
        samplesPerBlock = cfg.getSamplesPerBlock();
        lookahead       = 3;

        pcmBlock   = new short[cfg.getSamplesPerBlock() * numChannels];
        adpcmBlock = new byte [cfg.getBlockSize()];

        if (cfg.getNoiseShaping()) {
            shaping = cfg.getSampleRate() > 64000 ? NoiseShaping.STATIC : NoiseShaping.DYNAMIC;
        } else {
            shaping = NoiseShaping.OFF;
        }
    }

    /**
     * Creates a configuration object builder.
     * @return a configuration builder with default parameters
     */
    public static ADPCMEncoderConfig.Builder configure() {
        return new ADPCMEncoderConfig.Builder(null);
    }

    /**
     * Creates a builder for a copy of an existing configuration object.
     * @param other a configuration object from which to initialize the builder
     * @return a builder with the same parameters as the given configuration object
     */
    public static ADPCMEncoderConfig.Builder configure(ADPCMEncoderConfig other) {
        return new ADPCMEncoderConfig.Builder(other);
    }

    /**
     * @return this encoder's configuration object
     */
    public ADPCMEncoderConfig getConfiguration() {
        return config;
    }

    /**
     * Encodes a block of PCM data to an ADPCM block.
     * @param in   input buffer; contains 16-bit PCM data
     * @param out  output buffer for ADPCM data; capacity must be equal or greater than the value returned by {@link
     *             ADPCMEncoderConfig#getBlockSize()}
     * @return <tt>out</tt>
     * @throws IOException when an encoding problem occurs
     */
    public ByteBuffer encode(ShortBuffer in, ByteBuffer out) throws IOException {
        int currentBlockSize = blockSize;
        ADPCMContext ctx = null;

        while (in.hasRemaining()) {
            int blockAdpcmSamples = samplesPerBlock;
            int blockPcmSamples   = samplesPerBlock;
            int numSamples = in.remaining() / numChannels;

            if (blockPcmSamples > numSamples) {
                blockAdpcmSamples = ((numSamples + 6) & ~7) + 1;
                currentBlockSize  = (blockAdpcmSamples - 1) / (numChannels ^ 3) + (numChannels * 4);
                blockPcmSamples   = numSamples;
            }

            final int count = blockPcmSamples * numChannels;
            in.get(pcmBlock, 0, count);

            // if this is the last block and it's not full, duplicate the last sample(s) so we don't cerate problems
            // for the lookahead
            if (blockAdpcmSamples > blockPcmSamples) {
                int dst = blockPcmSamples * numChannels;
                int src = dst - numChannels;
                int dups = (blockAdpcmSamples - blockPcmSamples) * numChannels;

                while ((dups--)>0) {
                    pcmBlock[dst++] = pcmBlock[src++];
                }
            }

            if (ctx==null) {
                ctx = new ADPCMContext(blockAdpcmSamples, pcmBlock, numChannels);
            }

            int numBytes = encodeBlock(ctx, adpcmBlock, pcmBlock, blockAdpcmSamples);
            if (numBytes != currentBlockSize) {
                throw new ADPCMEncodingException(
                        "unexpected number of bytes encoded; " +
                        "expected "+currentBlockSize+", found "+numBytes
                );
            }

            out.put(adpcmBlock, 0, currentBlockSize);
        }

        return out;
    }

    private int encodeBlock(ADPCMContext ctx, byte[] outBuf, short[] inBuf, int inCount) {
        final int[]  initPcmData = new int[2];
        final byte[] initIndex   = new byte[2];
        int written = 0;

        getDecodeParameters(ctx, initPcmData, initIndex);

        int inPtr  = 0;
        int outPtr = 0;

        for (int ch=0; ch < ctx.getNumChannels(); ch++) {
            initPcmData[ch] = inBuf[inPtr++];
            outBuf[outPtr]   = (byte) initPcmData[ch];
            outBuf[outPtr+1] = (byte) (initPcmData[ch] >> 8);
            outBuf[outPtr+2] = initIndex[ch];
            outBuf[outPtr+3] = 0;

            outPtr += 4;
            written += 4;
        }

        setDecodeParameters(ctx, initPcmData, initIndex);
        written += encodeChunks(ctx, outBuf, outPtr, inBuf, inPtr, inCount);

        return written;
    }

    private int encodeChunks(ADPCMContext ctx, byte[] outBuf, int outPtr, short[] inBuf, int inPtr, int inCount) {
        int chunks = (inCount-1)/8;
        int written = chunks * 4 * ctx.getNumChannels();

        while ((chunks--)>0) {
            for (int ch=0; ch<ctx.getNumChannels(); ch++) {
                int pcmPtr = inPtr + ch;

                for (int i=0; i<4; i++) {
                    outBuf[outPtr] = encodeSample(ctx, ch, inBuf, pcmPtr, chunks * 8 + (3 - i) * 2 + 2);
                    pcmPtr += ctx.getNumChannels();
                    outBuf[outPtr] |= encodeSample(ctx, ch, inBuf, pcmPtr, chunks * 8 + (3 - i) * 2 + 1) << 4;
                    pcmPtr += ctx.getNumChannels();
                    outPtr++;
                }
            }

            inPtr += 8 * ctx.getNumChannels();
        }

        return written;
    }

    private byte encodeSample(ADPCMContext ctx, int ch, short[] inBuf, int inPtr, int numSamples) {
        ADPCMContext.Channel chan = ctx.getChannel(ch);
        int csample    = inBuf[inPtr];
        int depth      = numSamples -1;
        int step       = ADPCM.stepTable(chan.index);
        int trialDelta = step >> 3;

        switch (shaping) {
            case DYNAMIC:
                csample = shapeDynamic(chan, csample);
                break;
            case STATIC:
                csample = shapeStatic(chan, csample);
                break;
            case OFF:
                break;
        }

        if (depth > lookahead) {
            depth = lookahead;
        }

        int[] bestNibble = new int[1];
        minimumError(chan, ctx.getNumChannels(), csample, inBuf, inPtr, depth, bestNibble);
        int nibble = bestNibble[0];

        if ((nibble & 1)!=0) trialDelta += (step >> 2);
        if ((nibble & 2)!=0) trialDelta += (step >> 1);
        if ((nibble & 4)!=0) trialDelta += step;
        if ((nibble & 8)!=0) trialDelta = -trialDelta;

        chan.pcmData += trialDelta;
        chan.index   += ADPCM.indexTable(nibble & 0x07);
        chan.index = clip(chan.index, 0, 88);
        chan.pcmData = clip(chan.pcmData, -32768, 32767);

        if (shaping!=NoiseShaping.OFF) {
            chan.error += chan.pcmData;
        }

        return (byte) nibble;
    }

    private double minimumError(ADPCMContext.Channel pchan, int numChannels, int csample, short[] inBuf, int inPtr, int depth, int[] bestNibble) {
        ADPCMContext.Channel chan = new ADPCMContext.Channel(pchan);

        int delta      = csample - chan.pcmData;
        int step       = ADPCM.stepTable(chan.index);
        int trialDelta = (step >> 3);

        int    nibble;
        int    nibble2;
        double minError;

        if (delta < 0) {
            int mag = (-delta << 2) / step;
            nibble = 0x8 | (mag > 7 ? 7 : mag);
        }
        else {
            int mag = (delta << 2) / step;
            nibble = mag > 7 ? 7 : mag;
        }

        if ((nibble & 1)!=0) trialDelta += (step >> 2);
        if ((nibble & 2)!=0) trialDelta += (step >> 1);
        if ((nibble & 4)!=0) trialDelta += step;
        if ((nibble & 8)!=0) trialDelta = -trialDelta;

        chan.pcmData += trialDelta;
        chan.pcmData = clip(chan.pcmData, -32768, 32767);

        if (bestNibble!=null) {
            bestNibble[0] = nibble;
        }

        minError = (double) (chan.pcmData - csample) * (chan.pcmData - csample);

        if (depth!=0) {
            chan.index += ADPCM.indexTable(nibble & 0x07);
            chan.index = clip(chan.index, 0, 88);
            minError += minimumError(chan, numChannels, inBuf[inPtr+numChannels], inBuf, inPtr + numChannels, depth - 1, null);
        } else {
            return minError;
        }

        for (nibble2 = 0; nibble2 <= 0xF; ++nibble2) {
            double error;

            if (nibble2 == nibble) {
                continue;
            }

            chan.set(pchan);
            trialDelta = (step >> 3);

            if ((nibble2 & 1)!=0) trialDelta += (step >> 2);
            if ((nibble2 & 2)!=0) trialDelta += (step >> 1);
            if ((nibble2 & 4)!=0) trialDelta += step;
            if ((nibble2 & 8)!=0) trialDelta = -trialDelta;

            chan.pcmData += trialDelta;
            chan.pcmData = clip(chan.pcmData, -32768, 32767);

            error = (double) (chan.pcmData - csample) * (chan.pcmData - csample);

            if (error < minError) {
                chan.index += ADPCM.indexTable(nibble2 & 0x07);
                chan.index = clip(chan.index, 0, 88);
                error += minimumError(chan, numChannels, inBuf[inPtr+numChannels], inBuf, inPtr+numChannels, depth - 1, null);

                if (error < minError) {
                    if (bestNibble!=null) {
                        bestNibble[0] = nibble2;
                    }
                    minError = error;
                }
            }
        }

        return minError;
    }

    private int shapeDynamic(ADPCMContext.Channel chan, int csample) {
        int sam  = (3 * chan.history [0] - chan.history [1]) >> 1;
        int temp = csample - (((chan.weight * sam) + 512) >> 10);

        int shapingWeight;

        if (sam!=0 && temp!=0){
            chan.weight -= (((sam ^ temp) >> 29) & 4) - 2;
        }

        chan.history[1] = chan.history[0];
        chan.history[0] = csample;

        shapingWeight = (chan.weight < 256) ? 1024 : 1536 - (chan.weight * 2);
        temp = -((shapingWeight * chan.error + 512) >> 10);

        if (shapingWeight < 0 && temp!=0) {
            if (temp == chan.error) {
                temp = (temp < 0) ? temp + 1 : temp - 1;
            }

            chan.error = -csample;
            csample += temp;
        } else {
            chan.error = -(csample += temp);
        }

        return csample;
    }

    private int shapeStatic(ADPCMContext.Channel chan, int csample) {
        csample -= chan.error;
        chan.error = -csample;
        return csample;
    }

    private void getDecodeParameters(ADPCMContext ctx, int[] initPcmData, byte[] initIndex) {
        for (int ch=0; ch<ctx.getNumChannels(); ch++) {
            initPcmData[ch] = ctx.getChannel(ch).pcmData;
            initIndex  [ch] = ctx.getChannel(ch).index;
        }
    }

    private void setDecodeParameters(ADPCMContext ctx, int[] initPcmData, byte[] initIndex) {
        for (int ch=0; ch<ctx.getNumChannels(); ch++) {
            ctx.getChannel(ch).pcmData = initPcmData[ch];
            ctx.getChannel(ch).index   = initIndex  [ch];
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
