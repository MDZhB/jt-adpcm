package com.jiggawatt.jt.tools.adpcm.util;

import com.jiggawatt.jt.tools.adpcm.ADPCMDecoder;
import com.jiggawatt.jt.tools.adpcm.ADPCMDecoderConfig;
import com.jiggawatt.jt.tools.adpcm.ADPCMEncoder;
import com.jiggawatt.jt.tools.adpcm.ADPCMEncoderConfig;
import com.jiggawatt.jt.tools.adpcm.impl.ADPCMUtil;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static com.jiggawatt.jt.tools.adpcm.impl.RIFFUtil.*;

/**
 * This class provides WAV file input and output functionality for use with the ADPCM codec. Use the
 * {@link #fromADPCMBuffer(ByteBuffer, int, ADPCMEncoderConfig)} family of methods to create an instance from encoder
 * output, or the {@link #fromFile(Path)} method to obtain codec input from disk.
 * To write the file to a stream, use {@link #dump(OutputStream)}.
 *
 * @author Nikita Leonidov
 * @since  1.1.0
 */
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

    /**
     * Creates a new {@link WAVFile} instance from the given ADPCM-encoded buffer.
     * @param encodedData  the ADPCM-encoded audio
     * @param numSamples   the number of samples in {@code encodedData}
     * @param cfg          the encoder configuration with which {@code encodedData} was generated
     * @return a {@code WAVFile} with the given contents
     */
    public static WAVFile fromADPCMBuffer(ByteBuffer encodedData, int numSamples, ADPCMEncoderConfig cfg) {
        return fromADPCMBuffer(encodedData, numSamples, cfg.getChannels(), cfg.getSampleRate(), cfg.getBlockSize());
    }

    /**
     * Creates a new {@link WAVFile} instance from the given ADPCM-encoded buffer.
     * @param encodedData  the ADPCM-encoded audio
     * @param numSamples   the number of samples stored in the input buffer
     * @param channels     the number of channels stored in the input buffer
     * @param sampleRate   the sample rate of the input data in Hz
     * @param blockSize    the ADPCM block size for the input data
     * @return a {@code WAVFile} with the given contents
     */
    public static WAVFile fromADPCMBuffer(
            ByteBuffer encodedData,
            int numSamples,
            int channels,
            int sampleRate,
            int blockSize
    ) {
        final int samplesPerBlock = ADPCMUtil.computeSamplesPerBlock(channels, blockSize);

        WAVFile dst = new WAVFile();

        dst.factSamples       = numSamples;
        dst.format            = WAVE_FORMAT_IMA_ADPCM;
        dst.numSamples        = numSamples;
        dst.realBitsPerSample = 4;

        dst.formatTag        = WAVE_FORMAT_IMA_ADPCM;
        dst.numChannels      = channels;
        dst.sampleRate       = sampleRate;
        dst.bytesPerSecond   = ADPCMUtil.computeBytesPerSecond(sampleRate, blockSize, samplesPerBlock);
        dst.blockAlign       = blockSize;
        dst.rawBitsPerSample = 4;
        dst.cbSize           = 2;
        dst.union            = samplesPerBlock;
        dst.channelMask      = 0;
        dst.subFormat        = 0;
        dst.GUID             = new String(new byte[14]);

        dst.data = copyBuffer(encodedData);

        return dst;
    }

    /**
     * Creates a new {@link WAVFile} instance from the given PCM audio data.
     * @param pcmData     the PCM audio to store
     * @param channels    the number of samples in {@code pcmData}
     * @param sampleRate  the sample rate of the input sound in Hz
     * @return a {@code WAVFile} with the given contents
     */
    public static WAVFile fromPCMBuffer(ByteBuffer pcmData, int channels, int sampleRate) {
        WAVFile dst = new WAVFile();

        final int bytesPerSample = 2;
        final int bitsPerSample  = 16;
        final int numSamples     = pcmData.capacity() / (bytesPerSample * channels);

        dst.factSamples       = 0;
        dst.format            = WAVE_FORMAT_PCM;
        dst.numSamples        = numSamples;
        dst.realBitsPerSample = bitsPerSample;

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
        dst.GUID             = new String(new byte[14]);

        dst.data = copyBuffer(pcmData);

        return dst;
    }

    /**
     * Reads the file at the given path and produces a {@link WAVFile} with its contents.
     * @param filePath  the path to the input file as a string
     * @return a {@code WAVFile} with the given contents
     */
    public static WAVFile fromFile(String filePath) throws IOException {
        return WAVFile.fromFile(Paths.get(filePath));
    }

    /**
     * Reads the file at the given path and produces a {@link WAVFile} with its contents.
     * @param filePath  the path to the input file
     * @return a {@code WAVFile} with the given contents
     */
    public static WAVFile fromFile(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            return WAVFile.fromStream(in);
        }
    }

    /**
     * Creates a {@link WAVFile} from the contents of the given stream.
     * @param in  the stream from which to read the input WAV file
     * @return a {@code WAVFile} with the given contents
     */
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

    public int getChannels() {
        return numChannels;
    }

    /**
     * @return this file's sample rate in Hz
     */
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

    /**
     * The number of bytes in a block if this is an ADPCM-encoded file, or the total number of bytes in a sample if this
     * is a PCM file. Use this as the input to {@link ADPCMDecoderConfig.Builder#setBlockSize(int)}.
     * @return ADPCM block size, or the number of bytes in an audio sample
     */
    public int getBlockSize() {
        return blockAlign;
    }

    /**
     * Produces a read-only view of this file's audio data. Pass this to
     * {@link ADPCMDecoder#decode(ByteBuffer, ShortBuffer)} or as a {@link ShortBuffer} to
     * {@link ADPCMEncoder#encode(ShortBuffer, ByteBuffer)}.
     * @return a read-only copy of this file's audio data
     */
    public ByteBuffer getReadOnlyData() {
        return data.asReadOnlyBuffer().order(data.order()).rewind();
    }

    /**
     * Writes the contents of this object to the given output stream as a WAV file.
     * @param out  output stream for the file
     * @throws IOException  if an IO problem occurs
     */
    public void dump(OutputStream out) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);

        // create format chunk
        // =============================================================================================================
        final boolean isPcm = getFormat() == Format.PCM;

        final ByteBuffer fmtChunk =
            ByteBuffer
            .allocate(26 + 14) // size of format chunk = 8-byte header + 26 bytes + 14-byte format GUID remainder
            .order(ByteOrder.LITTLE_ENDIAN);

        fmtChunk.putShort((short) formatTag);
        fmtChunk.putShort((short) numChannels);
        fmtChunk.putInt  (sampleRate);
        fmtChunk.putInt  (bytesPerSecond);
        fmtChunk.putShort((short) blockAlign);
        fmtChunk.putShort((short) rawBitsPerSample);

        if (!isPcm) {
            fmtChunk.putShort((short) cbSize);
            fmtChunk.putShort((short) union);
            if (channelMask != 0 && subFormat != 0) {
                fmtChunk.putInt  (channelMask);
                fmtChunk.putShort((short) subFormat);
                fmtChunk.put     (GUID.getBytes(StandardCharsets.US_ASCII));
            }
        }

        fmtChunk.limit(fmtChunk.position()).rewind();

        // create optional fact chunk
        // =============================================================================================================
        final ByteBuffer factChunk;
        if (!isPcm) {
            factChunk = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            factChunk.putInt(factSamples);
            factChunk.rewind();
        } else {
            factChunk = ByteBuffer.allocate(0);
        }

        // create data chunk
        // =============================================================================================================
        // data chunk might include padding byte
        final int dataPadding = data.capacity() % 2 == 0 ? 0 : 1;

        // populate header
        // =============================================================================================================
        final int fileSize =
                4 + chunkSize(fmtChunk)
                + (factChunk.limit() > 0 ? chunkSize(factChunk) : 0)
                + chunkSize(data) + dataPadding;
        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(RIFF_ID);
        header.putInt(fileSize);
        header.putInt(WAVE_ID);

        // dump all chunks
        // =============================================================================================================
        out.write(header.array());
        dumpChunk(dataOut, fmtChunk,  FMT_ID);
        if (factChunk.limit() > 0) {
            dumpChunk(dataOut, factChunk, FACT_ID);
        }
        dumpChunk(dataOut, data, DATA_ID);

        // append padding bytes, if needed
        for (int i=0; i<dataPadding; i++) {
            out.write(0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WAVFile)) return false;
        WAVFile wavFile = (WAVFile) o;
        return factSamples == wavFile.factSamples
            && format == wavFile.format
            && numSamples == wavFile.numSamples
            && realBitsPerSample == wavFile.realBitsPerSample
            && formatTag == wavFile.formatTag
            && numChannels == wavFile.numChannels
            && sampleRate == wavFile.sampleRate
            && bytesPerSecond == wavFile.bytesPerSecond
            && blockAlign == wavFile.blockAlign
            && rawBitsPerSample == wavFile.rawBitsPerSample
            && cbSize == wavFile.cbSize
            && union == wavFile.union
            && channelMask == wavFile.channelMask
            && subFormat == wavFile.subFormat
            && GUID.equals(wavFile.GUID)
            && data.equals(wavFile.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            factSamples,
            format,
            numSamples,
            realBitsPerSample,
            formatTag,
            numChannels,
            sampleRate,
            bytesPerSecond,
            blockAlign,
            rawBitsPerSample,
            cbSize,
            union,
            channelMask,
            subFormat,
            GUID,
            data
        );
    }

    private WAVFile() {
        //
    }

    private int samplesPerBlock() {
        return union;
    }

    private static void dumpChunk(DataOutputStream out, ByteBuffer chunkData, int chunkId) throws IOException {
        out.writeInt(Integer.reverseBytes(chunkId));
        out.writeInt(Integer.reverseBytes(chunkData.limit()));
        if (chunkData.isDirect() || chunkData.isReadOnly()) {
            ByteBuffer dataView = chunkData.rewind().asReadOnlyBuffer();
            byte[] elements = new byte[512];

            while (dataView.remaining() >= elements.length) {
                final int nextPosition = dataView.position() + elements.length;
                dataView.get(elements).position(Math.min(dataView.limit(), nextPosition));
                out.write(elements);
            }

            if (dataView.hasRemaining()) {
                elements = new byte[dataView.remaining()];
                dataView.get(elements);
                out.write(elements);
            }
        } else {
            out.write(chunkData.array(), 0, chunkData.limit());
        }
    }

    private static void requireChunk(ByteBuffer fmtChunk, int riffId) throws IOException {
        if (fmtChunk == null) {
            throw new IOException("expected " + chunkId(riffId) + " chunk in file; none found");
        }
    }

    private static ByteBuffer copyChunk(ByteBuffer riffChunk, int chunkId, int chunkSize) {
        int offset = riffChunk.position();

        ByteBuffer copy =
            ByteBuffer.allocate(chunkSize)
            .order(ByteOrder.LITTLE_ENDIAN);

        copy.put(
            riffChunk.duplicate()
            .position(offset)
            .limit(offset + chunkSize)
        );

        riffChunk.position(offset + chunkSize);

        return copy.rewind();
    }

    private static int chunkSize(ByteBuffer chunkData) {
        return chunkData.limit() + 8;
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

        final boolean hasExtensionBytes = in.capacity() >= 40;

        dst.format = hasExtensionBytes && dst.formatTag == WAVE_FORMAT_EXTENSIBLE
                ? dst.subFormat
                : dst.formatTag;

        int validBitsPerSample = dst.union;

        dst.realBitsPerSample = hasExtensionBytes && validBitsPerSample!=0
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

        final int channels    = dst.numChannels;
        final int format      = dst.format;
        final int blockAlign  = dst.blockAlign;
        int       factSamples = dst.factSamples;

        // determine number of samples & validate
        // =============================================================================================================
        if (format == WAVE_FORMAT_PCM) {
            if (chunkSize % dst.blockAlign != 0) {
                throw new IOException("malformed WAV file; data chunk size is not a multiple of block alignment");
            }

            dst.numSamples = chunkSize / blockAlign;
        } else {
            int q = chunkSize / blockAlign;
            int r = chunkSize % blockAlign;

            int lastBlockSamples;

            dst.numSamples = q * dst.samplesPerBlock();

            if (r!=0) {
                if (r % (channels*4) != 0) {
                    throw new IOException("malformed WAV file");
                }

                lastBlockSamples = (r - (channels*4)) * (channels^3)+1;
                dst.numSamples += lastBlockSamples;
            } else {
                lastBlockSamples = dst.samplesPerBlock();
            }

            if (factSamples!=0) {
                if (factSamples < dst.numSamples && factSamples > dst.numSamples - lastBlockSamples) {
                    dst.numSamples = factSamples;
                } else if (
                    channels == 2 && (factSamples >>= 1) < dst.numSamples
                    && factSamples > dst.numSamples - lastBlockSamples
                ) {
                    dst.numSamples = factSamples;
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
        ByteBuffer data = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        in.get(dataBytes);

        // there might be a padding byte if chunkSize is odd
        if ((chunkSize%2)!=0) {
            in.get();
        }

        dst.data = data;
    }

    private static ByteBuffer copyBuffer(ByteBuffer src) {
        final int initialPosition = src.position();

        ByteBuffer copy     = ByteBuffer.allocate(src.remaining());
        byte[]     elements = new byte[256];

        while (src.remaining() >= elements.length) {
            final int nextPosition = src.position() + elements.length;
            src.get(elements).position(Math.min(src.limit(), nextPosition));
            copy.put(elements);
        }

        if (src.hasRemaining()) {
            elements = new byte[src.remaining()];
            src.get(elements);
            copy.put(elements);
        }

        src.position(initialPosition);

        return copy.rewind();
    }
}
