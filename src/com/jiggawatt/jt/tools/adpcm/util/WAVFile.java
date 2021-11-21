package com.jiggawatt.jt.tools.adpcm.util;

import com.jiggawatt.jt.tools.adpcm.ADPCMEncoderConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.jiggawatt.jt.tools.adpcm.impl.RIFFUtil.*;

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
    private int numSamples;
    private int realBitsPerSample;

    // raw header
    // =================================================================================================================
    private int formatTag;
    private int numChannels;
    private int sampleRate;
    private int bytesPerSecond;
    private int blockAlign;
    private int rawBitsPerSample;
    private int cbSize;
    private int union;
    private int channelMask;
    private int subFormat;   // this is actually the first 2 bytes of the GUID
    private String GUID;     // this is the remainder of the GUID

    private ByteBuffer data;

    // values for wave channel mask; see ksmedia.h from windows sdk
    private static final int SPEAKER_FRONT_LEFT     = 0x1;
    private static final int SPEAKER_FRONT_RIGHT    = 0x2;
    private static final int SPEAKER_FRONT_CENTER   = 0x4;
    private static final int KSAUDIO_SPEAKER_MONO   = SPEAKER_FRONT_CENTER;
    private static final int KSAUDIO_SPEAKER_STEREO = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT;

    public static WAVFile fromADPCMBuffer(ByteBuffer encodedData, int numSamples, ADPCMEncoderConfig cfg) {
        WAVFile dst = new WAVFile();

        dst.factSamples       = numSamples;
        dst.format            = WAVE_FORMAT_IMA_ADPCM;
        dst.numSamples        = numSamples;
        dst.realBitsPerSample = cfg.getSamplesPerBlock();

        dst.formatTag        = WAVE_FORMAT_EXTENSIBLE;
        dst.numChannels      = cfg.getChannels();
        dst.sampleRate       = cfg.getSampleRate();
        dst.bytesPerSecond   = cfg.getBytesPerSecond();
        dst.blockAlign       = cfg.getBlockSize();
        dst.rawBitsPerSample = 4;
        dst.cbSize           = 2;
        dst.union            = cfg.getSamplesPerBlock();
        dst.channelMask      = cfg.getChannels() == 2? KSAUDIO_SPEAKER_STEREO : KSAUDIO_SPEAKER_MONO;
        dst.subFormat        = WAVE_FORMAT_IMA_ADPCM;
        dst.GUID             = new String(new byte[14]); // empty!

        dst.data = encodedData.asReadOnlyBuffer().duplicate().rewind();

        return dst;
    }

    private static WAVFile fromPCMBuffer(ByteBuffer pcmData, int channels, int sampleRate) {
        WAVFile dst = new WAVFile();

        final int bytesPerSample = 2;
        final int bitsPerSample  = 16;
        final int numSamples     = pcmData.capacity() / (bytesPerSample * channels);

        dst.factSamples       = numSamples;
        dst.format            = WAVE_FORMAT_PCM;
        dst.numSamples        = numSamples;
        dst.realBitsPerSample = bytesPerSample;

        dst.formatTag        = WAVE_FORMAT_PCM;
        dst.numChannels      = channels;
        dst.sampleRate       = sampleRate;
        dst.bytesPerSecond   = sampleRate * channels * bytesPerSample;
        dst.blockAlign       = bytesPerSample * channels;
        dst.rawBitsPerSample = bitsPerSample;

        // the following members are not written for pcm files
        dst.cbSize           = 0;
        dst.union            = 0;
        dst.channelMask      = 0;
        dst.subFormat        = 0;
        dst.GUID             = null;

        dst.data = pcmData.asReadOnlyBuffer().duplicate().rewind();

        return dst;
    }

    public static WAVFile fromFile(String filePath) throws IOException {
        return WAVFile.fromFile(Paths.get(filePath));
    }

    public static WAVFile fromFile(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            return WAVFile.fromStream(in);
        }
    }

    public static WAVFile fromStream(InputStream in) throws IOException {
        WAVFile dst = new WAVFile();

        // read the main chunk to determine how many bytes we need to grab from the stream
        // =============================================================================================================
        // this stuff is ugly -- we should really be using a little-endian input stream or something, but the extra
        // dependency would be too much
        requireId(in, RIFF_ID);

        final int    riffSize   = intLittleEndian(in);
        final byte[] fileBytes  = new byte[riffSize];
        final int    actualSize = in.read(fileBytes);

        if (actualSize != riffSize) {
            throw new IOException("malformed file; expected "+riffSize+" bytes in RIFF chunk, but found "+actualSize);
        }

        ByteBuffer riffChunk = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);

        requireId(riffChunk, WAVE_ID);

        // find the chunks we need to properly read the audio in this file
        // =============================================================================================================
        ByteBuffer fmtChunk  = null;
        ByteBuffer factChunk = null;
        ByteBuffer dataChunk = null;

        while (riffChunk.hasRemaining()) {
            final int chunkId   = riffChunk.getInt();
            final int chunkSize = riffChunk.getInt();

            if (chunkId == FMT_ID && fmtChunk == null) {
                fmtChunk = copyChunk(riffChunk, chunkId, chunkSize);
            } else if (chunkId == FACT_ID && factChunk == null) {
                factChunk = copyChunk(riffChunk, chunkId, chunkSize);
            } else if (chunkId == DATA_ID && dataChunk == null) {
                dataChunk = copyChunk(riffChunk, chunkId, chunkSize);
            } else {
                skipChunk(riffChunk, chunkSize);
            }
        }

        requireChunk(fmtChunk,  FMT_ID);
        requireChunk(dataChunk, DATA_ID);

        // process each chunk
        // =============================================================================================================
        readFmtChunk(dst, fmtChunk);
        if (factChunk != null) {
            readFactChunk(dst, factChunk);
        }
        readDataChunk(dst, dataChunk);

        // we're done.
        // =============================================================================================================
        return dst;
    }

    public int getNumChannels() {
        return numChannels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getBitsPerSample() {
        return realBitsPerSample;
    }

    public Format getFormat() {
        return format==WAVE_FORMAT_PCM? Format.PCM : Format.IMA_ADPCM;
    }

    public int getNumSamples() {
        return numSamples;
    }

    public int getBlockSize() {
        return blockAlign;
    }

    public ByteBuffer getReadOnlyData() {
        return data.asReadOnlyBuffer().order(data.order()).rewind();
    }

    public void dump(OutputStream out) throws IOException {
        // create format chunk
        // =============================================================================================================
        final boolean isPcm = getFormat() == Format.PCM;

        final ByteBuffer fmtChunk =
            ByteBuffer.allocate(
                isPcm
                ? 8 + 16
                : 8 + 26 + 14 // size of format chunk = 8-byte header + 26 bytes + 14-byte format GUID remainder
            )
            .order(ByteOrder.LITTLE_ENDIAN);

        fmtChunk.putInt  (FMT_ID);
        fmtChunk.putInt  (fmtChunk.capacity() - 8);
        fmtChunk.putShort((short) formatTag);
        fmtChunk.putShort((short) numChannels);
        fmtChunk.putInt  (sampleRate);
        fmtChunk.putInt  (bytesPerSecond);
        fmtChunk.putShort((short) blockAlign);
        fmtChunk.putShort((short) rawBitsPerSample);

        if (!isPcm) {
            fmtChunk.putShort((short) cbSize);
            fmtChunk.putShort((short) union);
            fmtChunk.putInt  (channelMask);
            fmtChunk.putShort((short) subFormat);
            fmtChunk.put     (GUID.getBytes(StandardCharsets.US_ASCII));
        }

        fmtChunk.rewind();

        // create optional fact chunk
        // =============================================================================================================
        final ByteBuffer factChunk;
        if (!isPcm) {
            factChunk = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            factChunk.putInt(FACT_ID);
            factChunk.putInt(4);
            factChunk.putInt(factSamples);
            factChunk.rewind();
        } else {
            factChunk = ByteBuffer.allocate(0);
        }

        // create data chunk
        // =============================================================================================================
        // data chunk might include padding byte
        final int dataPadding      = data.capacity() % 2 == 0 ? 0 : 1;
        final int dataContentBytes = data.capacity() + dataPadding;
        final int dataTotalBytes   = dataContentBytes + 8;

        // populate header
        // =============================================================================================================
        final int fileSize = fmtChunk.capacity() + factChunk.capacity() + dataTotalBytes;
        ByteBuffer header = ByteBuffer.allocate(12);
        header.putInt(RIFF_ID);
        header.putInt(fileSize);
        header.putInt(WAVE_ID);

        // dump all chunks
        // =============================================================================================================
        out.write(header.array());
        out.write(fmtChunk.array());
        out.write(factChunk.array());
        out.write(DATA_ID);
        out.write(dataContentBytes);
        if (data.isDirect()) {
            ByteBuffer dataView = data.rewind().asReadOnlyBuffer();
            while (dataView.hasRemaining()) {
                out.write(dataView.get());
            }
        } else {
            out.write(data.array());
        }
        for (int i=0; i<dataPadding; i++) {
            out.write(0);
        }
    }

    private WAVFile() {
        //
    }

    private int samplesPerBlock() {
        return union;
    }

    private static void requireChunk(ByteBuffer fmtChunk, int riffId) throws IOException {
        if (fmtChunk == null) {
            throw new IOException("expected " + chunkId(riffId) + " chunk in file; none found");
        }
    }

    private static ByteBuffer copyChunk(ByteBuffer riffChunk, int chunkId, int chunkSize) {
        ByteBuffer ret = riffChunk
            .duplicate()
            .order(ByteOrder.LITTLE_ENDIAN)
            .limit(riffChunk.position()+chunkSize);
        riffChunk.position(riffChunk.position() + chunkSize);
        return ret;
    }

    private static void skipChunk(ByteBuffer riffChunk, int chunkSize) {
        riffChunk.position(riffChunk.position() + chunkSize);
    }

    private static void readFmtChunk(WAVFile dst, ByteBuffer in) throws IOException {
        dst.formatTag        = uint16(in);  // 2
        dst.numChannels      = uint16(in);  // 2
        dst.sampleRate       = uint32(in);  // 4
        dst.bytesPerSecond   = uint32(in);  // 4
        dst.blockAlign       = uint16(in);  // 2
        dst.rawBitsPerSample = uint16(in);  // 2
        dst.cbSize           = uint16(in);  // 2
        dst.union            = uint16(in);  // 2
        dst.channelMask      = uint32(in);  // 4
        dst.subFormat        = uint16(in);  // 2

        byte[] b = new byte[14];
        for (int i = 0; i < 14; i++) {
            b[i] = int8(in);
        }
        dst.GUID = new String(b);

        dst.format = dst.formatTag == WAVE_FORMAT_EXTENSIBLE && in.capacity()==40
                ? dst.subFormat
                : dst.formatTag;

        int validBitsPerSample = dst.union;

        dst.realBitsPerSample = in.capacity() == 40 && validBitsPerSample!=0
                ? validBitsPerSample
                : dst.rawBitsPerSample;

        // validate
        // =============================================================================================================
        if (dst.numChannels < 1 || dst.numChannels > 2) {
            throw new IOException("unsupported number of channels: "+dst.numChannels+"; expected mono or stereo");
        }

        if (dst.format == WAVE_FORMAT_PCM) {
            if (dst.realBitsPerSample != 16) {
                throw new IOException("unsupported bits per sample: " + dst.realBitsPerSample + "; expected 16");
            }

            if (dst.blockAlign != dst.numChannels*2) {
                throw new IOException("block alignment must match number of channels");
            }
        } else if (dst.format == WAVE_FORMAT_IMA_ADPCM) {
            if (dst.realBitsPerSample!=4) {
                throw new IOException("unsupported bits per sample: "+dst.realBitsPerSample+"; expected 4");
            }

            int expect = (dst.blockAlign - dst.numChannels*4) * (dst.numChannels^3) + 1;
            int samplesPerBlock = dst.union;
            if (samplesPerBlock != expect) {
                throw new IOException("malformed WAV file");
            }
        } else {
            throw new IOException("unsupported format; PCM or IMA ADPCM expected");
        }
    }

    private static int uint16(ByteBuffer in) {
        return in.hasRemaining() ? Short.toUnsignedInt(in.getShort()) : 0;
    }

    private static int uint32(ByteBuffer in) {
        return in.hasRemaining() ? in.getInt() : 0;
    }

    private static byte int8(ByteBuffer in) {
        return in.hasRemaining() ? in.get() : 0;
    }

    private static void readFactChunk(WAVFile dst, ByteBuffer in) {
        dst.factSamples = in.getInt();
    }

    private static void readDataChunk(WAVFile dst, ByteBuffer in) throws IOException {
        final int chunkSize = in.remaining();

        if (chunkSize==0) {
            throw new IOException("malformed WAV file: no samples");
        }

        // determine number of samples & validate
        // =============================================================================================================
        if (dst.format == WAVE_FORMAT_PCM) {
            if (chunkSize % dst.blockAlign != 0) {
                throw new IOException("malformed WAV file; data chunk size is not a multiple of block alignment");
            }

            dst.numSamples = chunkSize / dst.blockAlign;
        } else {
            int q = chunkSize / dst.blockAlign;
            int r = chunkSize % dst.blockAlign;

            int lastBlockSamples;

            dst.numSamples = q * dst.samplesPerBlock();

            if (r!=0) {
                if (r % (dst.numChannels*4) != 0) {
                    throw new IOException("malformed WAV file");
                }

                lastBlockSamples = (r - (dst.numChannels*4)) * (dst.numChannels^3)+1;
                dst.numSamples += lastBlockSamples;
            } else {
                lastBlockSamples = dst.samplesPerBlock();
            }

            if (dst.factSamples!=0) {
                if (dst.factSamples < dst.numSamples && dst.factSamples > dst.numSamples - lastBlockSamples) {
                    dst.numSamples = dst.factSamples;
                } else if (
                        dst.numChannels == 2 && (dst.factSamples >>= 1) < dst.numSamples
                    && dst.factSamples > dst.numSamples - lastBlockSamples
                ) {
                    dst.numSamples = dst.factSamples;
                }
            }
        }

        if (dst.numSamples==0) {
            throw new IOException("malformed WAV file: no samples");
        }

        // read data
        // =============================================================================================================
        // read data
        byte[] dataBytes = new byte[chunkSize];
        ByteBuffer data = ByteBuffer.wrap(dataBytes);
        if (dst.realBitsPerSample==16) {
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

        dst.data = data;
    }
}
