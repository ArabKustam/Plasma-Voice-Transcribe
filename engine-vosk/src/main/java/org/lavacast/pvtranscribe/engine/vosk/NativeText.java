package org.lavacast.pvtranscribe.engine.vosk;

import com.sun.jna.Native;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Safety net for the JNA string encoding. If JNA was already initialized with a non-UTF-8 encoding
 * (e.g. by -Djna.encoding=... or the OS default), strings coming from Vosk are re-decoded as UTF-8.
 */
final class NativeText {

    private static volatile Charset jnaCharset = StandardCharsets.UTF_8;

    private NativeText() {
    }

    static void init(PlatformLogger logger) {
        try {
            Charset charset = Charset.forName(Native.getDefaultStringEncoding());
            jnaCharset = charset;
            if (!charset.equals(StandardCharsets.UTF_8)) {
                logger.warn("JNA string encoding is " + charset + " instead of UTF-8; Vosk text will be re-decoded. "
                        + "For exact results start the server with -Djna.encoding=UTF-8.");
            }
        } catch (Throwable ignored) {
            jnaCharset = StandardCharsets.UTF_8;
        }
    }

    static String utf8(String fromJna) {
        Charset charset = jnaCharset;
        if (fromJna == null || charset.equals(StandardCharsets.UTF_8)) return fromJna;
        return new String(fromJna.getBytes(charset), StandardCharsets.UTF_8);
    }
}
