package com.jiggawatt.jt.tools.adpcm.data;

import com.jiggawatt.jt.tools.adpcm.util.WAVFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class TestUtils {
    public static WAVFile getClasspathWav(String name) throws IOException {
        try (InputStream in = TestUtils.class.getResourceAsStream(name)) {
            if (in==null) {
                throw new FileNotFoundException(name);
            }

            return WAVFile.fromStream(in);
        }
    }

    public static InputStream openClasspathStream(String name) throws IOException {
        InputStream in = TestUtils.class.getResourceAsStream(name);
        if (in == null) {
            throw new FileNotFoundException(name);
        }
        return in;
    }
}
