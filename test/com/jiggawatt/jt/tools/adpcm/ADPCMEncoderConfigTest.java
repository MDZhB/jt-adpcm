package com.jiggawatt.jt.tools.adpcm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.jiggawatt.jt.tools.adpcm.ADPCMEncoderConfig.AUTO_BLOCK_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ADPCMEncoderConfigTest {

    @ParameterizedTest
    @ValueSource(ints={1, 2})
    public void acceptsValidChannelNumbers(int channels) {
        assertEquals(
            channels,
            ADPCMEncoder.configure()
                .setChannels(channels)
                .end()
            .getChannels()
        );
    }

    @ParameterizedTest
    @ValueSource(ints={-1, 3})
    public void rejectsInvalidChannelNumbers(int channels) {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMEncoder.configure().setChannels(channels)
        );
    }

    @ParameterizedTest
    @ValueSource(ints={1, 2, 8000, 44100, 88200})
    public void acceptsPositiveSampleRates(int sampleRate) {
        assertEquals(
            sampleRate,
            ADPCMEncoder.configure()
                .setSampleRate(sampleRate)
                .end()
            .getSampleRate()
        );
    }

    @ParameterizedTest
    @ValueSource(ints={-1, 0})
    public void rejectsNonPositiveSampleRate(int sampleRate) {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMEncoder.configure().setSampleRate(sampleRate)
        );
    }

    @ParameterizedTest
    @ValueSource(ints={256, 512, 1024, 2048, 4096, 8192, 16384, 32768})
    public void acceptsPowerOfTwoBlockSizeInRange(int blockSize) {
        assertEquals(
            blockSize,
            ADPCMEncoder.configure()
                .setBlockSize(blockSize)
                .end()
            .getBlockSize()
        );
    }

    @Test
    public void rejectsNonPowerOfTwoBlockSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMEncoder.configure().setBlockSize(5)
        );
    }

    @Test
    public void rejectsSmallBlockSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMEncoder.configure().setBlockSize(128)
        );
    }

    @Test
    public void rejectsLargeBlockSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMEncoder.configure().setBlockSize(65536)
        );
    }

    @Test
    public void appliesNoiseShapingSetting() {
        assertFalse(
            ADPCMEncoder.configure()
                .setNoiseShaping(false)
                .end()
            .getNoiseShaping()
        );
    }

    @Test
    public void expectedDefaults() {
        ADPCMEncoderConfig def = ADPCMEncoder.configure().end();

        assertEquals(2,     def.getChannels());
        assertEquals(44100, def.getSampleRate());
        assertTrue  (def.getNoiseShaping());
    }

    @Test
    public void computesBlockSizeByDefault() {
        doTestAutoBlockSize(ADPCMEncoder.configure());
    }

    @Test
    public void computesBlockSizeForAutoSetting() {
        doTestAutoBlockSize(ADPCMEncoder.configure().setBlockSize(AUTO_BLOCK_SIZE));
    }

    private void doTestAutoBlockSize(ADPCMEncoderConfig.Builder builder) {
        assertEquals(
            256,
            builder
                .setSampleRate(8000)
                .setChannels(1)
                .end()
            .getBlockSize()
        );

        assertEquals(
            2048,
            builder
                .setSampleRate(44100)
                .setChannels(2)
                .end()
            .getBlockSize()
        );
    }

    @Test
    public void copyFactoryReproducesAllSettings() {
        ADPCMEncoderConfig src = ADPCMEncoder.configure()
            .setChannels(1)
            .setSampleRate(8000)
            .setNoiseShaping(false)
            .setBlockSize(256)
            .end();

        ADPCMEncoderConfig cpy = ADPCMEncoder.configure(src).end();

        assertEquals(src.getChannels(),     cpy.getChannels());
        assertEquals(src.getSampleRate(),   cpy.getSampleRate());
        assertEquals(src.getNoiseShaping(), cpy.getNoiseShaping());
        assertEquals(src.getBlockSize(),    cpy.getBlockSize());
    }
}
