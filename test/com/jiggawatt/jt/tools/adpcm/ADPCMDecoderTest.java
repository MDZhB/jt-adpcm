package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.data.TestUtils;
import com.jiggawatt.jt.tools.adpcm.util.WAVFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ADPCMDecoderTest {

    // decoding with dynamic noise shaping
    // =================================================================================================================
    @Test
    public void decode_16bit_44100Hz_stereo() throws IOException {
        doDecodeTest(16, 44100, 2, true);
    }

    @Test
    public void decode_16bit_8000Hz_stereo() throws IOException {
        doDecodeTest(16, 8000, 2, true);
    }

    @Test
    public void decode_16bit_44100Hz_mono() throws IOException {
        doDecodeTest(16, 44100, 1, true);
    }

    @Test
    public void decode_16bit_8000Hz_mono() throws IOException {
        doDecodeTest(16, 8000, 1, true);
    }
    
    // decoding with static noise shaping
    // =================================================================================================================
    @Test
    public void decode_16bit_88200Hz_mono() throws IOException {
        doDecodeTest(16, 88200, 1, true);
    }

    @Test
    public void decode_16bit_88200Hz_stereo() throws IOException {
        doDecodeTest(16, 88200, 2, true);
    }

    // decoding without noise shaping
    // =================================================================================================================
    @Test
    public void decode_16bit_44100Hz_stereo_flat() throws IOException {
        doDecodeTest(16, 44100, 2, false);
    }

    @Test
    public void decode_16bit_8000Hz_stereo_flat() throws IOException {
        doDecodeTest(16, 8000, 2, false);
    }

    @Test
    public void decode_16bit_44100Hz_mono_flat() throws IOException {
        doDecodeTest(16, 44100, 1, false);
    }

    @Test
    public void decode_16bit_8000Hz_mono_flat() throws IOException {
        doDecodeTest(16, 8000, 1, false);
    }

    private void doDecodeTest(int bits, int sampleRate, int channels, boolean shape) throws IOException {
        // load test data
        // -------------------------------------------------------------------------------------------------------------
        WAVFile inputWav  = TestUtils.getClasspathWav("adpcm_"+name(bits, sampleRate, channels, !shape));
        WAVFile expectWav = TestUtils.getClasspathWav("dec_"  +name(bits, sampleRate, channels, !shape));

        ADPCMDecoderConfig cfg =
            ADPCMDecoder.configure()
            .setChannels  (channels)
            .setBlockSize (inputWav.getBlockSize())
            .setSampleRate(sampleRate)
            .end();

        ByteBuffer  input  = inputWav.getReadOnlyData();
        ShortBuffer expect = expectWav.getReadOnlyData().asShortBuffer();

        // decode
        // -------------------------------------------------------------------------------------------------------------
        ShortBuffer actual =
            new ADPCMDecoder(cfg)
            .decode(
                input,
                ByteBuffer.allocate(inputWav.getNumSamples() * inputWav.getChannels() * 2)
                .asShortBuffer()
            )
            .rewind();

        // test decoded data
        // -------------------------------------------------------------------------------------------------------------
        while (expect.hasRemaining()) {
            assertEquals(expect.get(), actual.get());
        }

        assertFalse(expect.hasRemaining());
        assertFalse(actual.hasRemaining());
    }

    @Test
    public void rejectsBufferTooShort() throws IOException {
        WAVFile inputWav = TestUtils.getClasspathWav("adpcm_"+name(16, 8000, 1, true));

        ADPCMDecoderConfig cfg =
            ADPCMDecoder.configure()
            .setChannels  (1)
            .setBlockSize (inputWav.getBlockSize())
            .setSampleRate(8000)
            .end();

        ByteBuffer input = inputWav.getReadOnlyData();
        // make our buffer a byte too short
        input.limit(input.limit()-1);

        IOException e = assertThrows(IOException.class, () -> {
            new ADPCMDecoder(cfg)
            .decode(
                input,
                ByteBuffer.allocate(inputWav.getNumSamples() * inputWav.getChannels() * 2)
                .asShortBuffer()
            )
            .rewind();
        });

        assertEquals("too few elements left in input buffer", e.getMessage());
    }

    @Test
    public void rejectsJunkData() throws IOException {
        WAVFile inputWav = TestUtils.getClasspathWav("adpcm_"+name(16, 8000, 1, true));

        ADPCMDecoderConfig cfg =
            ADPCMDecoder.configure()
            .setChannels  (1)
            .setBlockSize (inputWav.getBlockSize())
            .setSampleRate(8000)
            .end();

        byte[] junkArray = new byte[inputWav.getReadOnlyData().remaining()];
        Arrays.fill(junkArray, (byte)90);

        IOException e = assertThrows(IOException.class, () -> {
            new ADPCMDecoder(cfg)
            .decode(
                ByteBuffer.wrap(junkArray),
                ByteBuffer.allocate(inputWav.getNumSamples() * inputWav.getChannels() * 2)
                .asShortBuffer()
            )
            .rewind();
        });

        assertEquals("malformed block header", e.getMessage());
    }

    /**
     * Generates a .wav file name for the given test configuration.
     * @param bits        sample bits
     * @param sampleRate  sample rate in Hz
     * @param channels    channels (1=mono, 2=stereo)
     * @param flat        true for files without dynamic noise shaping
     * @return a .wav filename
     */
    private static String name(int bits, int sampleRate, int channels, boolean flat) {
        return bits+"bit_"+sampleRate+"Hz_"+(channels==1?"mono":"stereo")+(flat?"_flat.wav":".wav");
    }


}
