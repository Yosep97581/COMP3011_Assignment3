/*
 * COMP3011 Assignment 3.
 * New file by Yosep Monier Nababan, with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.util.Objects;
import org.bytedeco.javacv.Frame;

/**
 * The subtitle effect stage in the ordered frame-processing pipeline.
 * It resolves the cue for THIS frame's timestamp, including in concurrent mode.
 *
 * The text is stored with the processed frame rather than painted into its
 * pixel buffer. The JavaFX view then composites it over the video so the CC
 * control can hide it immediately (even when paused), without decoding again.
 */
public final class FrameSubtitler extends FrameProcessor {
    private final SubtitleRenderState subtitles;

    public FrameSubtitler() {
        this(new SubtitleRenderState());
    }

    FrameSubtitler(SubtitleRenderState subtitles) {
        this.subtitles = Objects.requireNonNull(subtitles);
    }

    @Override
    public void process(Frame frame, InfoFrame info) {
        info.setSubtitle(subtitles.captionAt(info.timestampUs()));
    }
}
