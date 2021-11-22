package com.jiggawatt.jt.tools.adpcm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
