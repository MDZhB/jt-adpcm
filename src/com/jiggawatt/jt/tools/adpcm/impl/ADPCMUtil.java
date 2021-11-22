package com.jiggawatt.jt.tools.adpcm.impl;

public class ADPCMUtil {

    public static int computeOutputSize(int numSamples, int numChannels, int samplesPerBlock, int blockSize) {
        final int q = numSamples / samplesPerBlock;
        final int r = numSamples % samplesPerBlock;

        int ret = q * blockSize;

        if (r!=0) {
            int lastAdpcmBlockSamples = ((r + 6) & ~7) + 1;
            ret += (lastAdpcmBlockSamples - 1) / (numChannels ^ 3) + (numChannels * 4);
        }

        return ret;
    }

    public static int computeSamplesPerBlock(int numChannels, int blockSize) {
        return (blockSize - numChannels * 4) * (numChannels ^ 3) + 1;
    }

    public static int computeBlockSize(int channels, int sampleRate) {
        return 256 * channels * (sampleRate < 11000 ? 1 : sampleRate / 11000);
    }

    private ADPCMUtil() {

    }

    public static int computeBytesPerSecond(int sampleRate, int blockSize, int samplesPerBlock) {
        return sampleRate * blockSize / samplesPerBlock;
    }
}
