package org.lavacast.pvtranscribe.core.phrase;

import org.jetbrains.annotations.NotNull;
import org.lavacast.pvtranscribe.api.Registration;
import org.lavacast.pvtranscribe.api.Transcript;
import org.lavacast.pvtranscribe.api.phrase.MatchMode;
import org.lavacast.pvtranscribe.api.phrase.PhraseMatch;
import org.lavacast.pvtranscribe.api.phrase.PhraseRegistry;
import org.lavacast.pvtranscribe.api.phrase.PhraseTrigger;
import org.lavacast.pvtranscribe.api.text.TextNormalizer;
import org.lavacast.pvtranscribe.core.util.RateLimitedLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhraseRegistryImpl implements PhraseRegistry {

    private final CopyOnWriteArrayList<CompiledTrigger> triggers = new CopyOnWriteArrayList<>();
    private final RateLimitedLogger errors;

    public PhraseRegistryImpl(@NotNull RateLimitedLogger errors) {
        this.errors = errors;
    }

    @Override
    public @NotNull Registration register(@NotNull PhraseTrigger trigger) {
        CompiledTrigger compiled = new CompiledTrigger(trigger);
        triggers.add(compiled);
        return () -> triggers.remove(compiled);
    }

    @Override
    public @NotNull Collection<PhraseTrigger> getTriggers() {
        List<PhraseTrigger> list = new ArrayList<>();
        for (CompiledTrigger t : triggers) list.add(t.trigger);
        return list;
    }

    /**
     * Checks the transcript against all triggers.
     *
     * @param firedInUtterance ids of triggers already fired for this utterance; updated in place.
     *                         A trigger fires at most once per utterance.
     */
    public void evaluate(@NotNull Transcript transcript, @NotNull Set<String> firedInUtterance) {
        if (triggers.isEmpty() || transcript.isEmpty()) return;
        String text = transcript.getNormalizedText();
        for (CompiledTrigger compiled : triggers) {
            PhraseTrigger trigger = compiled.trigger;
            if (!transcript.isFinal() && !trigger.isMatchPartial()) continue;
            if (firedInUtterance.contains(trigger.getId())) continue;

            PhraseMatch match = compiled.match(text, transcript);
            if (match == null) continue;
            firedInUtterance.add(trigger.getId());
            dispatch(trigger, match);
        }
    }

    private void dispatch(PhraseTrigger trigger, PhraseMatch match) {
        Runnable task = () -> {
            try {
                trigger.getHandler().accept(match);
            } catch (Throwable t) {
                errors.warn("phrase:" + trigger.getId(), "Phrase trigger '" + trigger.getId() + "' threw an exception", t);
            }
        };
        if (trigger.getExecutor() == null) {
            task.run();
        } else {
            try {
                trigger.getExecutor().execute(task);
            } catch (Throwable t) {
                errors.warn("phrase-exec:" + trigger.getId(), "Could not schedule phrase trigger '" + trigger.getId() + "'", t);
            }
        }
    }

    private static final class CompiledTrigger {
        final PhraseTrigger trigger;
        final List<String> normalized = new ArrayList<>();
        final List<Pattern> patterns = new ArrayList<>();

        CompiledTrigger(PhraseTrigger trigger) {
            this.trigger = trigger;
            for (String phrase : trigger.getPhrases()) {
                if (trigger.getMode() == MatchMode.REGEX) {
                    patterns.add(Pattern.compile(phrase, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } else {
                    String n = TextNormalizer.normalize(phrase);
                    if (!n.isEmpty()) normalized.add(n);
                }
            }
        }

        PhraseMatch match(String text, Transcript transcript) {
            switch (trigger.getMode()) {
                case EXACT -> {
                    for (int i = 0; i < normalized.size(); i++) {
                        if (text.equals(normalized.get(i))) {
                            return new PhraseMatch(trigger.getId(), trigger.getPhrases().get(i), text, "", transcript);
                        }
                    }
                }
                case STARTS_WITH -> {
                    for (int i = 0; i < normalized.size(); i++) {
                        String p = normalized.get(i);
                        if (text.equals(p) || text.startsWith(p + " ")) {
                            return new PhraseMatch(trigger.getId(), trigger.getPhrases().get(i), p,
                                    text.substring(p.length()).trim(), transcript);
                        }
                    }
                }
                case CONTAINS -> {
                    for (int i = 0; i < normalized.size(); i++) {
                        String p = normalized.get(i);
                        if (TextNormalizer.containsPhrase(text, p)) {
                            int idx = text.indexOf(p);
                            return new PhraseMatch(trigger.getId(), trigger.getPhrases().get(i), p,
                                    text.substring(idx + p.length()).trim(), transcript);
                        }
                    }
                }
                case REGEX -> {
                    for (int i = 0; i < patterns.size(); i++) {
                        Matcher m = patterns.get(i).matcher(text);
                        if (m.find()) {
                            return new PhraseMatch(trigger.getId(), trigger.getPhrases().get(i), m.group(),
                                    text.substring(m.end()).trim(), transcript);
                        }
                    }
                }
            }
            return null;
        }
    }
}
