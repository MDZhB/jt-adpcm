package com.jiggawatt.jt.tools.adpcm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.jiggawatt.jt.tools.adpcm.ADPCMEncoderConfig.AUTO_BLOCK_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ADPCMDecoderConfigTest {

    @ParameterizedTest
    @ValueSource(ints={1, 2})
    public void acceptsValidChannelNumbers(int channels) {
        assertEquals(
            channels,
            ADPCMDecoder.configure()
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
            () -> ADPCMDecoder.configure().setChannels(channels)
        );
    }

    @ParameterizedTest
    @ValueSource(ints={1, 2, 8000, 44100, 88200})
    public void acceptsPositiveSampleRates(int sampleRate) {
        assertEquals(
            sampleRate,
            ADPCMDecoder.configure()
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
            () -> ADPCMDecoder.configure().setSampleRate(sampleRate)
        );
    }

    @ParameterizedTest
    @ValueSource(ints={256, 512, 1024, 2048, 4096, 8192, 16384, 32768})
    public void acceptsPowerOfTwoBlockSizeInRange(int blockSize) {
        assertEquals(
            blockSize,
            ADPCMDecoder.configure()
                .setBlockSize(blockSize)
                .end()
            .getBlockSize()
        );
    }

    @Test
    public void rejectsNonPowerOfTwoBlockSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMDecoder.configure().setBlockSize(5)
        );
    }

    @Test
    public void rejectsSmallBlockSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMDecoder.configure().setBlockSize(128)
        );
    }

    @Test
    public void rejectsLargeBlockSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ADPCMDecoder.configure().setBlockSize(65536)
        );
    }

    @Test
    public void expectedDefaults() {
        ADPCMDecoderConfig def = ADPCMDecoder.configure().end();
        assertEquals(2,     def.getChannels());
        assertEquals(44100, def.getSampleRate());
    }

    @Test
    public void computesBlockSizeByDefault() {
        doTestAutoBlockSize(ADPCMDecoder.configure());
    }

    @Test
    public void computesBlockSizeForAutoSetting() {
        doTestAutoBlockSize(ADPCMDecoder.configure().setBlockSize(AUTO_BLOCK_SIZE));
    }

    @ParameterizedTest
    @MethodSource
    public void computesBytesPerSecond(int expect, int channels, int sampleRate, int blockSize) {
        assertEquals(
            expect,
            ADPCMDecoder.configure()
                .setChannels(channels)
                .setSampleRate(sampleRate)
                .setBlockSize(blockSize)
                .end()
            .getBytesPerSecond()
        );
    }

    private static List<Arguments> computesBytesPerSecond() {
        return List.of(
            //         expect | channels | sampleRate | blockSize
            arguments(   4055,         1,        8000,        256),
            arguments(   8224,         2,        8000,        256),
            arguments(  22355,         1,       44100,        256),
            arguments(  45339,         2,       44100,        256),
            arguments(   4027,         1,        8000,        512),
            arguments(   8110,         2,        8000,        512),
            arguments(  22201,         1,       44100,        512),
            arguments(  44711,         2,       44100,        512)
        );
    }

    private void doTestAutoBlockSize(ADPCMDecoderConfig.Builder builder) {
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
        ADPCMDecoderConfig src = ADPCMDecoder.configure()
            .setChannels(1)
            .setSampleRate(8000)
            .setBlockSize(256)
            .end();

        ADPCMDecoderConfig cpy = ADPCMDecoder.configure(src).end();

        assertEquals(src.getChannels(),     cpy.getChannels());
        assertEquals(src.getSampleRate(),   cpy.getSampleRate());
        assertEquals(src.getBlockSize(),    cpy.getBlockSize());
    }
}
