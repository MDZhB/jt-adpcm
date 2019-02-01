# jt-adpcm
[![Javadocs](https://www.javadoc.io/badge/com.github.mdzhb/jt-adpcm.svg)](https://www.javadoc.io/doc/com.github.mdzhb/jt-adpcm)

## Summary
`jt-adpcm` provides a high-quality ADPCM encoder and an ADPCM decoder for Java 9+. It is a port of David Bryant's [ADPCM-XQ](https://github.com/dbry/adpcm-xq) library. 

## Usage
### Maven
Add the following to the `<dependencies>` element in your `pom.xml`:
```xml
<dependency>
  <groupId>com.github.mdzhb</groupId>
  <artifactId>jt-adpcm</artifactId>
  <version>1.0.0</version>
</dependency>
```
### Encoding
To encode 16-bit PCM data, place it into a `java.nio.ShortBuffer`, create an `ADPCMEncoderConfig`, then use the configuration to instantiate an `ADPCMEncoder`. The `ADPCMEncoderConfig`'s `computeOuputSize(ShortBuffer)` method will return the minimum size of the output buffer for the given input.
```java
ByteBuffer encodePCM(ShortBuffer pcmInput, int channels, int sampleRate, boolean shape) {
    
    ADPCMEncoderConfig cfg = 
        ADPCMEncoder.configure()
        .setChannels    (channels)   // 1 for mono, 2 for stereo
        .setSampleRate  (sampleRate) // sample rate in Hz
        .setNoiseShaping(shape)      // noise shaping; true=on, false=off
        .end();                      // create the configuration object
    
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
