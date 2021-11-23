package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.impl.ADPCMUtil;

/**
 * Configuration object for {@link ADPCMDecoder}. Acquire an instance using {@link ADPCMDecoder#configure()}.
 * @author Nikita Leonidov
 */
public final class ADPCMDecoderConfig {

    private int channels;
    private int blockSize;
    private int sampleRate;
    private int samplesPerBlock;

    public static final int AUTO_BLOCK_SIZE = -1;

    /**
     * Configures and produces instances of {@link ADPCMDecoderConfig}. Use {@link ADPCMDecoder#configure()} to obtain
     * an instance of this class.
     * @author Nikita Leonidov
     */
    public static final class Builder {
        private int     channels   = 2;
        private int     blockSize  = AUTO_BLOCK_SIZE;
        private int     sampleRate = 44100;

        Builder(ADPCMDecoderConfig other) {
            if (other==null) {
                return;
            }

            channels   = other.getChannels();
            blockSize  = other.getBlockSize();
            sampleRate = other.getSampleRate();
        }

        /**
         * Sets the number of output channels.
         * @param count  1 for mono, 2 for stereo
         * @return this builder
         */
        public Builder setChannels(int count) {
            if (count!=1 && count!=2) {
                throw new IllegalArgumentException(
                    "unsupported channel count: "+count+"; mono (1) or stereo (2) expected");
            }

            channels = count;
            return this;
        }

        /**
         * Sets the output sample rate. The default value is 44100.
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
         * Sets the input block size in bytes.
         * @param size  a power of two between 256 and 32768 inclusive, or {@link #AUTO_BLOCK_SIZE} to compute the block
         *              size automatically with the formula used by {@link ADPCMEncoderConfig}
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
        public ADPCMDecoderConfig end() {
            ADPCMDecoderConfig ret = new ADPCMDecoderConfig();

            ret.channels        = channels;
            ret.blockSize       = blockSize == AUTO_BLOCK_SIZE ? ADPCMUtil.computeBlockSize(channels, sampleRate) : blockSize;
            ret.sampleRate      = sampleRate;
            ret.samplesPerBlock = ADPCMUtil.computeSamplesPerBlock(channels, ret.blockSize);

            return ret;
        }
    }

    private ADPCMDecoderConfig() {
        // nada
    }

    /**
     * @return the number of output channels (1 for mono, 2 for stereo)
     * @see ADPCMDecoderConfig.Builder#setChannels(int)
     */
    public int getChannels() {
        return channels;
    }

    /**
     * @return the output block size in bytes
     * @see ADPCMDecoderConfig.Builder#setBlockSize(int)
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * @return the output sample rate in Hz
     * @see ADPCMDecoderConfig.Builder#setSampleRate(int)
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * @return the input block size in samples
     */
    public int getSamplesPerBlock() {
        return samplesPerBlock;
    }

    public int getBytesPerSecond() {
        return ADPCMUtil.computeBytesPerSecond(sampleRate, blockSize, samplesPerBlock);
    }
}
