/*
 * COMP3011 Assignment 3.
 * New file by Yosep Monier Nababan, with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subtitle data shared by the JavaFX controller and independent frame workers.
 * A single immutable snapshot prevents workers from reading a mismatched
 * subtitle track and CC state while a request finishes or the user toggles CC.
 */
final class SubtitleRenderState {
    record Caption(long revision, String text) { }

    private record State(SubtitleTrack track, boolean enabled, long revision) { }

    private final AtomicReference<State> current = new AtomicReference<>(
            new State(new SubtitleTrack(List.of()), false, 0));

    void setTrack(SubtitleTrack track) {
        Objects.requireNonNull(track);
        current.updateAndGet(old -> new State(track, old.enabled(), old.revision() + 1));
    }

    void setEnabled(boolean enabled) {
        current.updateAndGet(old -> new State(old.track(), enabled, old.revision() + 1));
    }

    long revision() {
        return current.get().revision();
    }

    Caption captionAt(long mediaTimestampUs) {
        State state = current.get();
        return new Caption(state.revision(), state.enabled()
                ? state.track().textAt(mediaTimestampUs) : "");
    }
}
