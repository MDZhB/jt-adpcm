package com.jiggawatt.jt.tools.adpcm;

import java.io.IOException;

/**
 * Thrown by {@link ADPCMEncoder} when an encoding problem occurs.
 * @author Nikita Leonidov
 */
public final class ADPCMEncodingException extends IOException {
    private static final long serialVersionUID = -783410334729595353L;

    ADPCMEncodingException(String m) {
        super(m);
    }
}
