/* COMP3011 Assignment 3 tests, with assistance from OpenAI ChatGPT. */
package comp3011;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrameSubtitlerTest {
    @Test
    void attachesCurrentCueToFrameMetadataAndClearsItAfterCue() throws Exception {
        SubtitleRenderState shared = new SubtitleRenderState();
        shared.setTrack(new SubtitleTrack(List.of(
                new SubtitleCue(1_000_000L, 3_000_000L, "Hello there!"))));
        shared.setEnabled(true);
        FrameProcessor processor = new FrameSubtitler(shared);

        InfoFrame first = new InfoFrame(24, 1_500_000L);
        processor.process(null, first);
        assertEquals("Hello there!", first.subtitleText());
        assertEquals(shared.revision(), first.subtitleRevision());

        InfoFrame later = new InfoFrame(96, 4_000_000L);
        processor.process(null, later);
        assertEquals("", later.subtitleText());
    }

    @Test
    void toggleAndTrackChangeInvalidateAnAlreadyPreparedCaption() {
        SubtitleRenderState shared = new SubtitleRenderState();
        shared.setTrack(new SubtitleTrack(List.of(
                new SubtitleCue(0L, 2_000_000L, "A caption"))));
        shared.setEnabled(true);
        FrameSubtitler processor = new FrameSubtitler(shared);
        InfoFrame oldFrame = new InfoFrame(1, 100_000L);
        processor.process(null, oldFrame);

        shared.setEnabled(false);
        assertNotEquals(shared.revision(), oldFrame.subtitleRevision());
        InfoFrame hiddenFrame = new InfoFrame(2, 200_000L);
        processor.process(null, hiddenFrame);
        assertEquals("", hiddenFrame.subtitleText());

        shared.setEnabled(true);
        shared.setTrack(new SubtitleTrack(List.of(new SubtitleCue(0L, 2_000_000L, "Updated"))));
        InfoFrame newFrame = new InfoFrame(3, 300_000L);
        processor.process(null, newFrame);
        assertEquals("Updated", newFrame.subtitleText());
    }

    @Test
    void subtitleEffectUsesSharedStateWithinOrderedProcessorChain() throws Exception {
        SubtitleRenderState shared = new SubtitleRenderState();
        shared.setTrack(new SubtitleTrack(List.of(new SubtitleCue(0L, 1_000_000L, "Hi"))));
        shared.setEnabled(true);
        List<FrameProcessor> chain = FrameEffect.createProcessors(
                List.of(FrameEffect.SUBTITLE, FrameEffect.SUBTITLE), shared);
        InfoFrame info = new InfoFrame(2, 400_000L);
        for (FrameProcessor processor : chain) {
            processor.process(null, info);
        }
        assertEquals("Hi", info.subtitleText());
    }
}
