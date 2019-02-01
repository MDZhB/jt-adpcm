package com.jiggawatt.jt.tools.adpcm;

final class ADPCMContext {

    static final class Channel {
        /** Current PCM value. */
        int pcmData;
        /** For noise shaping. */
        int error;
        /** For noise shaping. */
        int weight;
        /** For noise shaping. */
        final int[] history = new int[2];
        /** Current index into the step size table. */
        byte index;

        Channel() {}

        Channel(Channel other) {
            set(other);
        }

        Channel set(Channel other) {
            pcmData    = other.pcmData;
            error      = other.error;
            weight     = other.weight;
            history[0] = other.history[0];
            history[1] = other.history[1];
            index      = other.index;

            return this;
        }
    }

    private final Channel[] channels;

    ADPCMContext(int blockAdpcmSamples, short[] pcmBlock, int numChannels) {
        channels = new Channel[numChannels];
        for (int i=0; i<numChannels; i++) {
            channels[i] = new Channel();
        }

        int[] initialDeltas = computeInitialDeltas(blockAdpcmSamples, pcmBlock);

        for (int ch=0; ch < numChannels; ch++) {
            for (byte i=0; i<=88; i++) {
                if (i==88 || initialDeltas[ch] < (ADPCM.stepTable(i) + ADPCM.stepTable(i+1))/2) {
                    channels[ch].index = i;
                    break;
                }
            }
        }
    }

    int getNumChannels() {
        return channels.length;
    }

    Channel getChannel(int ch) {
        return channels[ch];
    }

    private int[] computeInitialDeltas(int blockAdpcmSamples, short[] pcmBlock) {
        final int numChannels = channels.length;
        int[] initialDeltas = new int[2];

        if (numChannels == 2) {
            for (int i=(blockAdpcmSamples-1) * numChannels; i>0; i-=numChannels) {
                initialDeltas[0] -= initialDeltas[0] >> 3;
                initialDeltas[0] += Math.abs(pcmBlock[i] - pcmBlock[i-numChannels]);

                initialDeltas[1] -= initialDeltas[1] >> 3;
                initialDeltas[1] += Math.abs(pcmBlock[i-1] - pcmBlock[i+1]);
            }
        } else {
            for (int i =(blockAdpcmSamples-1) * numChannels; i>0; i-=numChannels) {
                initialDeltas[0] -= initialDeltas[0] >> 3;
                initialDeltas[0] += Math.abs(pcmBlock[i] - pcmBlock[i - numChannels]);
            }
        }

        initialDeltas[0] >>= 3;
        initialDeltas[1] >>= 3;

        return initialDeltas;
    }
}
