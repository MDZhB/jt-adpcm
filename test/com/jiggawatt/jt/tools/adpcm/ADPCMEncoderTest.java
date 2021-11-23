package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.data.TestUtils;
import com.jiggawatt.jt.tools.adpcm.util.WAVFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ADPCMEncoderTest {

    // encoding with dynamic noise shaping
    // =================================================================================================================
    @Test
    public void encode_16bit_44100Hz_stereo() throws IOException {
        doTest(16, 44100, 2, true);
    }

    @Test
    public void encode_16bit_8000Hz_stereo() throws IOException {
        doTest(16, 8000, 2, true);
    }

    @Test
    public void encode_16bit_44100Hz_mono() throws IOException {
        doTest(16, 44100, 1, true);
    }

    @Test
    public void encode_16bit_8000Hz_mono() throws IOException {
        doTest(16, 8000, 1, true);
    }

    // encoding with static noise shaping
    //==================================================================================================================
    @Test
    public void encode_16bit_88200Hz_mono() throws IOException {
        doTest(16, 88200, 1, true);
    }

    @Test
    public void encode_16bit_88200Hz_stereo() throws IOException {
        doTest(16, 88200, 2, true);
    }

    // encoding without noise shaping
    // =================================================================================================================
    @Test
    public void encode_16bit_44100Hz_stereo_flat() throws IOException {
        doTest(16, 44100, 2, false);
    }

    @Test
    public void encode_16bit_8000Hz_stereo_flat() throws IOException {
        doTest(16, 8000, 2, false);
    }

    @Test
    public void encode_16bit_44100Hz_mono_flat() throws IOException {
        doTest(16, 44100, 1, false);
    }

    @Test
    public void encode_16bit_8000Hz_mono_flat() throws IOException {
        doTest(16, 8000, 1, false);
    }

    private void doTest(int bits, int sampleRate, int channels, boolean shape) throws IOException {
        // load test data
        // -------------------------------------------------------------------------------------------------------------
        WAVFile inputWav  = TestUtils.getClasspathWav("pcm_" + name(bits, sampleRate, channels, false));
        WAVFile expectWav = TestUtils.getClasspathWav("adpcm_"+name(bits, sampleRate, channels, !shape));

        ADPCMEncoderConfig cfg = ADPCMEncoder.configure()
            .setChannels(channels)
            .setSampleRate(sampleRate)
            .setNoiseShaping(shape)
            .end();

        ShortBuffer input  = inputWav.getReadOnlyData().asShortBuffer();
        ByteBuffer  expect = expectWav.getReadOnlyData();

        // encode
        // -------------------------------------------------------------------------------------------------------------
        ByteBuffer actual =
            new ADPCMEncoder(cfg)
            .encode(
                input,
                ByteBuffer.allocate(cfg.computeOutputSize(input)))
            .rewind();

        // test encoded data
        // -------------------------------------------------------------------------------------------------------------
        assertEquals(expect.capacity(), actual.capacity());
        while (expect.hasRemaining()) {
            assertEquals(expect.get(), actual.get());
        }

        assertFalse(expect.hasRemaining());
        assertFalse(actual.hasRemaining());
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
