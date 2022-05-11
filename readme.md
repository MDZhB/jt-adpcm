# jt-adpcm
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mdzhb/jt-adpcm)](https://repo1.maven.org/maven2/com/github/mdzhb/jt-adpcm/) [![Javadocs](https://www.javadoc.io/badge/com.github.mdzhb/jt-adpcm.svg)](https://www.javadoc.io/doc/com.github.mdzhb/jt-adpcm)

## Summary
`jt-adpcm` provides a high-quality ADPCM encoder and an ADPCM decoder for Java 9+. It is a port of David Bryant's [ADPCM-XQ](https://github.com/dbry/adpcm-xq) library. 

## Usage
### Maven
Add the following to the `<dependencies>` element in your `pom.xml`:
```xml
<dependency>
  <groupId>com.github.mdzhb</groupId>
  <artifactId>jt-adpcm</artifactId>
  <version>1.1.0</version>
</dependency>
```
### Encoding
To encode 16-bit PCM data, place it into a `java.nio.ShortBuffer`, create an `ADPCMEncoderConfig`, then use the configuration to instantiate an `ADPCMEncoder`. The `ADPCMEncoderConfig`'s `computeOuputSize(ShortBuffer)` method will return the minimum size of the output buffer for the given input.
```java
ByteBuffer encodePCM(ShortBuffer pcmInput, int channels, int sampleRate, boolean shape) {
    ADPCMEncoderConfig cfg = 
        ADPCMEncoder.configure()
        .setChannels    (channels)                           // 1 for mono, 2 for stereo
        .setSampleRate  (sampleRate)                         // sample rate in Hz
        .setNoiseShaping(shape)                              // noise shaping; true=on, false=off
        .setBlockSize   (ADPCMDecoderConfig.AUTO_BLOCK_SIZE) // compute block size automatically
        .end();                                              // create the configuration object
    
    // ADPCMEncoderConfig.computeOutputSize(ShortBuffer) computes the minimum output buffer size
    ByteBuffer adpcmOutput = ByteBuffer.allocate(cfg.computeOutputSize(pcmInput));
    
    // this returns the adpcmOutput buffer
    return new ADPCMEncoder(cfg).encode(pcmInput, adpcmOutput).rewind();
}
```
### Decoding
To decode ADPCM data to 16-bit PCM, place the ADPCM data into a `java.nio.ByteBuffer`, create an `ADPCMDecoderConfig`, then use the configuration to instantiate an `ADPCMDecoder`. The decoder computes the number of samples it must decode with `samples = inputBuffer.remaining() / decoderConfig.getChannels()`. 
```java
ShortBuffer decodeADPCM(ByteBuffer adpcmInput, int channels, int samples, int sampleRate) {
    ADPCMDecoderConfig cfg =
        ADPCMDecoder.configure()
        .setChannels  (channels)                           // 1 for mono, 2 for stereo
        .setSampleRate(sampleRate)                         // sample rate in Hz
        .setBlockSize (ADPCMDecoderConfig.AUTO_BLOCK_SIZE) // compute block size with the formula used by the encoder
        .end();                                            // create the configuration object
    
    // if `samples` is the length of the sound in samples, we need to double the length of the buffer for stereo data
    ShortBuffer pcmOutput = ShortBuffer.allocate(channels * samples);
    
    // this returns the pcmOutput buffer
    return new ADPCMDecoder(cfg).decode(adpcmInput, pcmOutput).rewind();
}
```
### File I/O
To read and write WAV files, use the `com.jiggawatt.jt.tools.adpcm.util.WAVFile` class. The following example reads a PCM file using an `InputStream`, encodes it, and dumps the resulting ADPCM audio to another file. It uses the information stored in the input WAV header to configure the encoder.
```java
void encode(boolean shape, InputStream in, OutputStream out) throws IOException {
    // use one of the `WAVFile` factory methods to obtain an instance
    WAVFile     wavInput = WAVFile.fromStream(in);
    // `WAVFile.getReadOnlyData()` provides you with a read-only view of the audio data stored in the file
    ShortBuffer pcmInput = wavInput.getReadOnlyData().asShortBuffer();

    // the input `WAVFile` gives us some of the information we need to configure the encoder
    ADPCMEncoderConfig cfg =
        ADPCMEncoder.configure()
        .setChannels    (wavInput.getChannels())
        .setSampleRate  (wavInput.getSampleRate())
        .setNoiseShaping(shape)
        .setBlockSize   (ADPCMDecoderConfig.AUTO_BLOCK_SIZE)
        .end();

    ByteBuffer adpcmOutput = ByteBuffer.allocate(cfg.computeOutputSize(pcmInput));

    // this convenience method generates WAV header info from your encoder configuration
    WAVFile wavOutput = WAVFile.fromADPCMBuffer(adpcmOutput, wavInput.getNumSamples(), cfg);

    // finally, WAVFile.dump() writes the file to the given stream 
    wavOutput.dump(out);
}
```
Dumping a decoded file works similarly.
```java
void decode(InputStream in, OutputStream out) throws IOException {
    WAVFile    wavInput   = WAVFile.fromStream(in);
    ByteBuffer adpcmInput = wavInput.getReadOnlyData();

    // the input `WAVFile` gives us the information we need to configure the decoder
    ADPCMDecoderConfig cfg =
        ADPCMDecoder.configure()
        .setChannels  (wavInput.getChannels())
        .setSampleRate(wavInput.getSampleRate())
        .setBlockSize (wavInput.getBlockSize())
        .end();

    ByteBuffer pcmOutput = ByteBuffer.allocate(wavInput.getNumSamples() * wavInput.getChannels() * 2);
    new ADPCMDecoder(cfg).decode(adpcmInput, pcmOutput.asShortBuffer());

    // create a pcm wav file from the decoder's output
    WAVFile wavOutput = WAVFile.fromPCMBuffer(pcmOutput, wavInput.getChannels(), wavInput.getSampleRate());

    // finally, WAVFile.dump() writes the file to the given stream 
    wavOutput.dump(out);
}
```
