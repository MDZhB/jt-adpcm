package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.data.TestUtils;
import com.jiggawatt.jt.tools.adpcm.util.WAVFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;

@RunWith(Parameterized.class)
public class WAVInputOutputTest {

    private final String inputFileName;

    @Parameters
    public static String[] testFileNames() {
        return new String[] {
            "adpcm_16bit_8000Hz_mono.wav",
            "adpcm_16bit_8000Hz_stereo.wav",
            "adpcm_16bit_44100Hz_mono.wav",
            "adpcm_16bit_44100Hz_stereo.wav",
            "pcm_16bit_8000Hz_mono.wav",
            "pcm_16bit_8000Hz_stereo.wav",
            "pcm_16bit_44100Hz_mono.wav",
            "pcm_16bit_44100Hz_stereo.wav",
        };
    }

    public WAVInputOutputTest(String inputFileName) {
        this.inputFileName = inputFileName;
    }

    @Test
    public void loadedFileIdenticalWhenDumped() throws IOException {
        new com.jiggawatt.jt.tools.adpcm.data.WAVFile(inputFileName);

        WAVFile file = TestUtils.getClasspathWav(inputFileName);
        final byte[] dumped;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            file.dump(out);
            dumped = out.toByteArray();
        }

        final byte[] original;
        try (InputStream in = TestUtils.openClasspathStream(inputFileName)) {
            original = in.readAllBytes();
        }

        assertArrayEquals(original, dumped);
    }
}
