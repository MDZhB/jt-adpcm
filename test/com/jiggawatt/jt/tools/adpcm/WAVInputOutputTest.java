package com.jiggawatt.jt.tools.adpcm;

import com.jiggawatt.jt.tools.adpcm.data.TestUtils;
import com.jiggawatt.jt.tools.adpcm.util.WAVFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WAVInputOutputTest {

    public static List<String> pcmFileNames() {
        return List.of(
            "pcm_16bit_8000Hz_mono.wav",
            "pcm_16bit_8000Hz_stereo.wav",
            "pcm_16bit_44100Hz_mono.wav",
            "pcm_16bit_44100Hz_stereo.wav"
        );
    }

    public static List<String> adpcmFileNames() {
        return List.of(
            "adpcm_16bit_8000Hz_mono.wav",
            "adpcm_16bit_8000Hz_stereo.wav",
            "adpcm_16bit_44100Hz_mono.wav",
            "adpcm_16bit_44100Hz_stereo.wav"
        );
    }

    public static List<String> allFileNames() {
        return Stream.concat(pcmFileNames().stream(), adpcmFileNames().stream()).collect(Collectors.toList());
    }

    @ParameterizedTest
    @MethodSource("allFileNames")
    public void loadedFileIdenticalWhenDumped(String inputFileName) throws IOException {
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

    @ParameterizedTest
    @MethodSource("pcmFileNames")
    public void generatesPlayableAdpcmWavWithEncoder(String inputFileName) throws IOException {
        WAVFile pcmFile = TestUtils.getClasspathWav(inputFileName);

        ADPCMEncoderConfig cfg = ADPCMEncoder.configure()
            .setChannels  (pcmFile.getChannels())
            .setSampleRate(pcmFile.getSampleRate())
            .setBlockSize (ADPCMEncoderConfig.AUTO_BLOCK_SIZE)
            .end();

        ByteBuffer adpcmOutput = ByteBuffer.allocate(cfg.computeOutputSize(pcmFile.getNumSamples()));
        new ADPCMEncoder(cfg).encode(pcmFile.getReadOnlyData().asShortBuffer(), adpcmOutput);

        WAVFile adpcmFileActual = WAVFile.fromADPCMBuffer(adpcmOutput.rewind(), pcmFile.getNumSamples(), cfg);
        WAVFile adpcmFileExpect = TestUtils.getClasspathWav("ad"+inputFileName);

        assertEquals(adpcmFileExpect, adpcmFileActual);
    }

    @ParameterizedTest
    @MethodSource("adpcmFileNames")
    public void generatesPlayableAdpcmWav(String inputFileName) throws IOException {

        WAVFile adpcmFileExpect = TestUtils.getClasspathWav(inputFileName);
        WAVFile adpcmFileActual = WAVFile.fromADPCMBuffer(
            adpcmFileExpect.getReadOnlyData(),
            adpcmFileExpect.getNumSamples(),
            adpcmFileExpect.getChannels(),
            adpcmFileExpect.getSampleRate(),
            adpcmFileExpect.getBlockSize()
        );

        assertEquals(adpcmFileExpect, adpcmFileActual);
    }

    @ParameterizedTest
    @MethodSource("pcmFileNames")
    public void generatesPlayablePcmWav(String inputFileName) throws IOException {
        if (!inputFileName.startsWith("pcm")) {
            return;
        }

        WAVFile pcmFileExpect = TestUtils.getClasspathWav(inputFileName);
        WAVFile pcmFileActual = WAVFile.fromPCMBuffer(pcmFileExpect.getReadOnlyData(), pcmFileExpect.getChannels(), pcmFileExpect.getSampleRate());

        assertEquals(pcmFileExpect, pcmFileActual);
    }
}
