package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.impl.ADPCMUtil;

import java.nio.ShortBuffer;

/**
 * Configuration object for {@link ADPCMEncoder}. Acquire an instance using {@link ADPCMEncoder#configure()}.
 * @author Nikita Leonidov
 */
public final class ADPCMEncoderConfig {

    private int     channels;
    private int     sampleRate;
    private boolean noiseShaping;

    private int blockSize;
    private int samplesPerBlock;

    public static final int AUTO_BLOCK_SIZE = -1;

    public static final class Builder {
        private int     channels     = 2;
        private int     sampleRate   = 44100;
        private boolean noiseShaping = true;
        private int     blockSize    = AUTO_BLOCK_SIZE;

        Builder(ADPCMEncoderConfig other) {
            if (other==null) {
                return;
            }

            channels     = other.getChannels();
            sampleRate   = other.getSampleRate();
            noiseShaping = other.getNoiseShaping();
            blockSize    = other.blockSize;
        }

        /**
         * Sets the number of input channels. The default value is 2.
         * @param count  1 for mono, 2 for stereo
         * @return this builder
         */
        public Builder setChannels(int count) {
            if (count!=1 && count!=2) {
                throw new IllegalArgumentException("unsupported channel count: "+count+"; mono (1) or stereo (2) expected");
            }

            channels = count;
            return this;
        }

        /**
         * Sets the input sample rate. The default value is 44100.
         * @param rate  sample rate in Hz; must be greater than 0
         * @return this builder
         */
        public Builder setSampleRate(int rate) {
            if (rate<1) {
                throw new IllegalArgumentException("unsupported sample rate: "+rate+"; must be greater than 0");
            }

            sampleRate = rate;
            return this;
        }

        /**
         * Enables or disables dynamic noise shaping. Encoding with noise shaping reduces apparent quantization noise.
         * Dynamic noise shaping is enabled by default.
         * @param on  <tt>true</tt> to enable dynamic noise shaping, <tt>false</tt> to disable
         * @return this builder
         */
        public Builder setNoiseShaping(boolean on) {
            noiseShaping = on;
            return this;
        }

        /**
         * Sets the output block size in bytes. A larger block size increases compression, but decreases sound quality;
         * a smaller block size decreases compression, but increases sound quality.
         * @param size  a power of two between 256 and 32768 inclusive, or {@link #AUTO_BLOCK_SIZE} to compute the block
         *              size automatically
         * @return this builder
         */
        public Builder setBlockSize(int size) {
            if (size!=AUTO_BLOCK_SIZE) {
                if (size < 256 || size > 32768 || Integer.bitCount(size) != 1) {
                    throw new IllegalArgumentException(
                        "unsupported block size: " + size +
                        "; must be AUTO_BLOCK_SIZE, or a power of two >= 256 and <= 32768"
                    );
                }
            }

            blockSize = size;
            return this;
        }

        /**
         * Creates a configuration object with the parameters passed to this builder.
         * @return a new configuration object
         */
        public ADPCMEncoderConfig end() {
            ADPCMEncoderConfig ret = new ADPCMEncoderConfig();

            ret.channels     = channels;
            ret.sampleRate   = sampleRate;
            ret.noiseShaping = noiseShaping;
            ret.blockSize    = blockSize == AUTO_BLOCK_SIZE ? ADPCMUtil.computeBlockSize(channels, sampleRate) : blockSize;

            ret.samplesPerBlock = ADPCMUtil.computeSamplesPerBlock(channels, ret.blockSize);

            return ret;
        }
    }

    private ADPCMEncoderConfig() {
        // nada
    }

    /**
     * @return the number of input channels (1 for mono, 2 for stereo)
     * @see ADPCMEncoderConfig.Builder#setChannels(int)
     */
    public int getChannels() {
        return channels;
    }

    /**
     * @return the input sample rate in Hz
     * @see ADPCMEncoderConfig.Builder#setSampleRate(int)
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * @return <tt>true</tt> when noise shaping is on, <tt>false</tt> when off
     * @see ADPCMEncoderConfig.Builder#setNoiseShaping(boolean)
     */
    public boolean getNoiseShaping() {
        return noiseShaping;
    }

    /**
     * @return the output block size in bytes
     * @see ADPCMEncoderConfig.Builder#setBlockSize(int)
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * @return the output block size in samples
     */
    public int getSamplesPerBlock() {
        return samplesPerBlock;
    }

    public int getBytesPerSecond() {
        return ADPCMUtil.computeBytesPerSecond(sampleRate, blockSize, samplesPerBlock);
    }

    /**
     * Computes the total number of bytes that will be generated by this encoder for the given input.
     * @param in  16-bit PCM data
     * @return the number of bytes required to store the input with ADPCM encoding
     */
    public int computeOutputSize(ShortBuffer in) {
        return ADPCMUtil.computeOutputSize(in.capacity()/channels, channels, samplesPerBlock, blockSize);
    }

    /**
     * Computes the total number of bytes that will be generated by this encoder for the given input.
     * @param numSamples number of input samples
     * @return the number of bytes required to store an input of the given length with ADPCM encoding
     */
    public int computeOutputSize(int numSamples) {
        int blockSize       = ADPCMUtil.computeBlockSize(channels, sampleRate);
        int samplesPerBlock = ADPCMUtil.computeSamplesPerBlock(channels, blockSize);

        return ADPCMUtil.computeOutputSize(numSamples, channels, samplesPerBlock, blockSize);
    }

}
