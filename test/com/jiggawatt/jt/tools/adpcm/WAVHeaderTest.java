package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.data.TestUtils;
import com.jiggawatt.jt.tools.adpcm.util.WAVFile;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

import static com.jiggawatt.jt.tools.adpcm.util.WAVFile.Format.*;

public class WAVHeaderTest {

    @Test
    public void readsMonoPcmFileHeader() throws IOException {
        try (InputStream in = TestUtils.openClasspathStream("pcm_16bit_8000Hz_mono.wav")) {
            WAVFile file = WAVFile.fromStream(in);

            assertEquals(1,     file.getChannels());
            assertEquals(8000,  file.getSampleRate());
            assertEquals(16,    file.getBitsPerSample());
            assertEquals(PCM,   file.getFormat());
            assertEquals(53127, file.getNumSamples());
            assertEquals(2,     file.getBlockSize());
        }
    }

    @Test
    public void readsStereoPcmFileHeader() throws IOException {
        try (InputStream in = TestUtils.openClasspathStream("pcm_16bit_8000Hz_stereo.wav")) {
            WAVFile file = WAVFile.fromStream(in);

            assertEquals(2,     file.getChannels());
            assertEquals(8000,  file.getSampleRate());
            assertEquals(16,    file.getBitsPerSample());
            assertEquals(PCM,   file.getFormat());
            assertEquals(53127, file.getNumSamples());
            assertEquals(4,     file.getBlockSize());
        }
    }

    @Test
    public void readsMonoAdpcmFileHeader() throws IOException {
        try (InputStream in = TestUtils.openClasspathStream("adpcm_16bit_8000Hz_mono.wav")) {
            WAVFile file = WAVFile.fromStream(in);

            assertEquals(1,         file.getChannels());
            assertEquals(8000,      file.getSampleRate());
            assertEquals(4,         file.getBitsPerSample());
            assertEquals(IMA_ADPCM, file.getFormat());
            assertEquals(53127,     file.getNumSamples());
            assertEquals(256,       file.getBlockSize());
        }
    }

    @Test
    public void readsStereoAdpcmFileHeader() throws IOException {
        try (InputStream in = TestUtils.openClasspathStream("adpcm_16bit_8000Hz_stereo.wav")) {
            WAVFile file = WAVFile.fromStream(in);

            assertEquals(2,         file.getChannels());
            assertEquals(8000,      file.getSampleRate());
            assertEquals(4,         file.getBitsPerSample());
            assertEquals(IMA_ADPCM, file.getFormat());
            assertEquals(53127,     file.getNumSamples());
            assertEquals(512,       file.getBlockSize());
        }
    }
}
