/*
 * Based on COMP3011 Assignment 2 starter code supplied by Simon Ratcliffe.
 * Assignment 3 changes by Yosep Monier Nababan, with assistance from OpenAI ChatGPT.
 */
package comp3011;

/** Metadata travelling with one frame through the effect pipeline. */
public final class InfoFrame {
    private final int frameNumber;
    private final long timestampUs;
    // Only the subtitle frame processor writes these values. The displaying
    // thread reads them after the worker has returned its completed frame.
    private String subtitleText = "";
    private long subtitleRevision = -1;

    public InfoFrame(int frameNumber, long timestampUs) {
        this.frameNumber = frameNumber;
        this.timestampUs = timestampUs;
    }

    public int frameNumber() { return frameNumber; }
    public long timestampUs() { return timestampUs; }
    public String subtitleText() { return subtitleText; }
    public long subtitleRevision() { return subtitleRevision; }

    void setSubtitle(SubtitleRenderState.Caption caption) {
        subtitleText = caption.text();
        subtitleRevision = caption.revision();
    }

    // Preserve the value semantics of the original InfoFrame record. Dynamic
    // subtitle metadata does not change the identity of a decoded frame.
    @Override
    public boolean equals(Object other) {
        return other instanceof InfoFrame that
                && frameNumber == that.frameNumber && timestampUs == that.timestampUs;
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(frameNumber) + Long.hashCode(timestampUs);
    }

    @Override
    public String toString() {
        return "InfoFrame[frameNumber=" + frameNumber + ", timestampUs=" + timestampUs + "]";
    }
}
