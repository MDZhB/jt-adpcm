package com.jiggawatt.jt.tools.adpcm.data;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WAVFile {

    private static final int RIFF_ID = chunkId("RIFF");
    private static final int WAVE_ID = chunkId("WAVE");
    private static final int FMT_ID  = chunkId("fmt ");
    private static final int FACT_ID = chunkId("fact");
    private static final int DATA_ID = chunkId("data");

    private static final int WAVE_FORMAT_PCM        = 0x1;
    private static final int WAVE_FORMAT_IMA_ADPCM  = 0x11;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xfffe;

    public enum Format {
        PCM,
        IMA_ADPCM
    }

    private int factSamples;
    private int format;
    private int bitsPerSample;
    private int numSamples;
    private int numChannels;
    private int sampleRate;
    private int blockAlign;

    private ByteBuffer data;

    public WAVFile(String name) throws IOException {
        ByteBuffer in = readFile(name).order(ByteOrder.LITTLE_ENDIAN);

        // read RIFF header
        //--------------------------------------------------------------------------------------------------------------
        requireId(in, RIFF_ID);
        final int riffSize = in.getInt();
        requireId(in, WAVE_ID);

        // loop through header elements until we reach the data chunk
        //--------------------------------------------------------------------------------------------------------------
        WaveHeader waveHeader = null;

        while (in.hasRemaining()) {
            final int chunkId   = in.getInt();
            final int chunkSize = in.getInt();

            if (chunkId == FMT_ID) {
                waveHeader = new WaveHeader(in.duplicate().order(ByteOrder.LITTLE_ENDIAN).limit(in.position()+chunkSize));
                in.position(in.position()+chunkSize);

                format = waveHeader.formatTag == WAVE_FORMAT_EXTENSIBLE && chunkSize==40
                        ? waveHeader.subFormat
                        : waveHeader.formatTag;

                bitsPerSample = chunkSize == 40 && waveHeader.validBitsPerSample()!=0
                        ? waveHeader.validBitsPerSample()
                        : waveHeader.bitsPerSample;

                validateFmtChunk(waveHeader, format, bitsPerSample);

            } else if (chunkId == FACT_ID) {
                factSamples = in.getInt();
                if (chunkSize-4 > 0) {
                    in.position(in.position()+chunkSize-4);
                }
            } else if (chunkId == DATA_ID) {
                if (waveHeader==null) {
                    throw new IOException("malformed WAV file: missing fmt chunk");
                }

                if (chunkSize==0) {
                    throw new IOException("malformed WAV file: no samples");
                }

                if (format == WAVE_FORMAT_PCM) {
                    if (chunkSize % waveHeader.blockAlign != 0) {
                        throw new IOException("malformed WAV file");
                    }

                    numSamples = chunkSize / waveHeader.blockAlign;
                } else {
                    int q = chunkSize / waveHeader.blockAlign;
                    int r = chunkSize % waveHeader.blockAlign;

                    int lastBlockSamples;

                    numSamples = q * waveHeader.samplesPerBlock();

                    if (r!=0) {
                        if (r%(waveHeader.numChannels*4) != 0) {
                            throw new IOException("malformed WAV file");
                        }

                        lastBlockSamples = (r - (waveHeader.numChannels*4)) * (waveHeader.numChannels^3)+1;
                        numSamples += lastBlockSamples;
                    } else {
                        lastBlockSamples = waveHeader.samplesPerBlock();
                    }

                    if (factSamples!=0) {
                        if (factSamples < numSamples && factSamples > numSamples - lastBlockSamples) {
                            numSamples = factSamples;
                        } else if (waveHeader.numChannels == 2 && (factSamples >>= 1) < numSamples
                                && factSamples > numSamples - lastBlockSamples) {

                            numSamples = factSamples;
                        }
                    }
                }

                if (numSamples==0) {
                    throw new IOException("malformed WAV file: no samples");
                }

                numChannels = waveHeader.numChannels;
                sampleRate  = waveHeader.sampleRate;
                blockAlign  = waveHeader.blockAlign;

                // read data
                byte[] dataBytes = new byte[chunkSize];
                data = ByteBuffer.wrap(dataBytes);
                if (bitsPerSample==16) {
                    int count = dataBytes.length/2;
                    for (int i=0; i<count; i++) {
                        data.putShort(in.getShort());
                    }
                } else {
                    in.get(dataBytes);
                }
                // there might be a padding byte if chunkSize is odd
                if ((chunkSize%2)!=0) {
                    in.get();
                }
            } else {
                // ignore unknown chunks
                in.position(in.position()+chunkSize);
            }
        }

        if (data==null) {
            throw new IOException("malformed WAV file: missing data chunk");
        }
    }

    public int getNumChannels() {
        return numChannels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }

    public Format getFormat() {
        return format==WAVE_FORMAT_PCM? Format.PCM : Format.IMA_ADPCM;
    }

    public int getNumSamples() {
        return numSamples;
    }

    public int getBlockAlign() {
        return blockAlign;
    }

    public ByteBuffer getReadOnlyData() {
        return data.asReadOnlyBuffer().order(data.order()).rewind();
    }

    private static void validateFmtChunk(WaveHeader waveHeader, int format, int bitsPerSample) throws IOException {
        if (waveHeader.numChannels < 1 || waveHeader.numChannels > 2) {
            throw new IOException("unsupported number of channels: "+
                    waveHeader.numChannels+"; expected mono or stereo");
        }

        if (format == WAVE_FORMAT_PCM) {
            if (bitsPerSample != 16) {
                throw new IOException("unsupported bits per sample: " + bitsPerSample + "; expected 16");
            }

            if (waveHeader.blockAlign != waveHeader.numChannels*2) {
                throw new IOException("block alignment must match number of channels");
            }
        } else if (format == WAVE_FORMAT_IMA_ADPCM) {
            if (bitsPerSample!=4) {
                throw new IOException("unsupported bits per sample: "+bitsPerSample+"; expected 4");
            }

            int expect = (waveHeader.blockAlign - waveHeader.numChannels*4) * (waveHeader.numChannels^3) + 1;
            if (waveHeader.samplesPerBlock() != expect) {
                throw new IOException("malformed WAV file");
            }
        } else {
            throw new IOException("unsupported format; PCM or IMA ADPCM expected");
        }
    }

    private static final class WaveHeader {
        int formatTag;
        int numChannels;
        int sampleRate;
        int bytesPerSecond;
        int blockAlign;
        int bitsPerSample;
        int cbSize;

        /** validBitsPerSample / samplesPerBlock / reserved */
        int union;

        int channelMask;
        int subFormat;

        String GUID;

        WaveHeader(ByteBuffer in) {
            formatTag   = uint16(in);
            numChannels = uint16(in);

            sampleRate     = uint32(in);
            bytesPerSecond = uint32(in);

            blockAlign = uint16(in);
            bitsPerSample = uint16(in);

            cbSize = uint16(in);

            union = uint16(in);

            channelMask = uint32(in);
            subFormat = uint16(in);

            byte[] b = new byte[14];
            for (int i = 0; i < 14; i++) {
                b[i] = int8(in);
            }
            GUID = new String(b);
        }

        int validBitsPerSample() {
            return union;
        }

        int samplesPerBlock() {
            return union;
        }

        int reserved() {
            return union;
        }

        private static int uint16(ByteBuffer in) {
            return in.hasRemaining()?Short.toUnsignedInt(in.getShort()):0;
        }

        private static int uint32(ByteBuffer in) {
            return in.hasRemaining()?in.getInt():0;
        }

        private static byte int8(ByteBuffer in) {
            return in.hasRemaining()?in.get():0;
        }
    }

    private static ByteBuffer readFile(String name) throws IOException {
        try (InputStream in = WAVFile.class.getResourceAsStream(name)) {
            if (in==null) {
                throw new FileNotFoundException(name);
            }

            byte[] dat = in.readAllBytes();
            return ByteBuffer.wrap(dat);
        }
    }

    private static int chunkId(String id) {
        byte[] b = id.getBytes();
        return b[0] | (b[1]<<8) | (b[2]<<16) | (b[3]<<24);
    }

    private static void requireId(ByteBuffer in, int expect) throws IOException {
        int actual = in.getInt();
        if (actual!=expect) {
            throw new IOException("malformed WAV file");
        }
    }
}
