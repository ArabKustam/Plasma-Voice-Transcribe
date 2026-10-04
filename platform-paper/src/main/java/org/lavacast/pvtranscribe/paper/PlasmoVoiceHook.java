package org.lavacast.pvtranscribe.paper;

import org.lavacast.pvtranscribe.core.session.TranscriptionService;
import org.lavacast.pvtranscribe.core.util.PlatformLogger;
import org.lavacast.pvtranscribe.voice.plasmo.PlasmoVoiceAdapter;

/**
 * Kept in its own class so Plasmo Voice classes are only loaded after we checked that Plasmo Voice
 * is installed; otherwise the plugin would fail with NoClassDefFoundError.
 */
final class PlasmoVoiceHook {

    private PlasmoVoiceHook() {
    }

    static void register(TranscriptionService service, PlatformLogger logger) throws Exception {
        service.registerVoiceSource(new PlasmoVoiceAdapter(logger));
    }
}
