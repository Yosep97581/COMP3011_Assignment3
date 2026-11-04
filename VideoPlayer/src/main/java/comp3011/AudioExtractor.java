/*
 * COMP3011 Assignment 3.
 * Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

/**
 * Extracts the media's audio into a temporary, compressed M4A file.
 * A separate grabber is important: the player's decoder is owned by the
 * JavaFX playback loop and must not be accessed by this background worker.
 */
public final class AudioExtractor {
    private static final long MAX_UPLOAD_BYTES = 25_000_000L;

    private AudioExtractor() { }

    public static Path extract(File videoFile) throws Exception {
        Path output = Files.createTempFile("comp3011-subtitles-", ".m4a");
        boolean completed = false;
        try {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();
                int channels = grabber.getAudioChannels();
                int sampleRate = grabber.getSampleRate();
                if (channels <= 0 || sampleRate <= 0) {
                    throw new IOException("This media file has no audio track.");
                }

                try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(output.toFile(), channels)) {
                    // AAC in M4A is much smaller than a raw WAV upload.
                    // The source is the video's decoded audio, not speaker output
                    // and not the audio after the player's mute setting.
                    recorder.setFormat("ipod");
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setAudioBitrate(96_000);
                    recorder.setAudioChannels(channels);
                    recorder.setSampleRate(sampleRate);
                    recorder.start();

                    Frame samples;
                    while ((samples = grabber.grabSamples()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Audio extraction was cancelled.");
                        }
                        if (samples.samples != null) {
                            recorder.recordSamples(sampleRate, channels, samples.samples);
                        }
                        if (Files.size(output) > MAX_UPLOAD_BYTES) {
                            throw new IOException("Extracted audio exceeds the 25 MB API limit.");
                        }
                    }
                    recorder.stop();
                }
            }
            if (Files.size(output) == 0 || Files.size(output) > MAX_UPLOAD_BYTES) {
                throw new IOException("The extracted audio is empty or exceeds 25 MB.");
            }
            completed = true;
            return output;
        } finally {
            if (!completed) {
                Files.deleteIfExists(output);
            }
        }
    }
}
