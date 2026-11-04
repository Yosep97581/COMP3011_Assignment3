/*
 * COMP3011 Assignment 3.
 * Yosep Monier Nababan (a1939679), with assistance from OpenAI ChatGPT.
 */
package comp3011;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Convert the SubtitleServer JSON response into timed, readable captions. */
public final class WhisperSubtitleParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long ONE_SECOND_US = 1_000_000L;
    private static final long MAX_CUE_US = 7_000_000L;
    private static final long PAUSE_BREAK_US = 550_000L;

    private WhisperSubtitleParser() { }

    public static SubtitleTrack parse(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        if (root == null || !root.isObject() || !root.path("words").isArray()
                || !root.path("segments").isArray()) {
            throw new IOException("The subtitle server returned invalid subtitle data.");
        }

        List<TimedWord> words = readWords(root.path("words"));
        if (words.isEmpty()) {
            // A response can contain valid segments but no word timestamps.
            words = approximateWordsFromSegments(root.path("segments"));
        }
        words.sort(Comparator.comparingLong(TimedWord::startUs));
        return new SubtitleTrack(groupWords(words));
    }

    private static List<TimedWord> readWords(JsonNode items) {
        List<TimedWord> words = new ArrayList<>();
        for (JsonNode item : items) {
            String text = item.path("word").asText("").strip();
            TimedWord word = readTimedWord(item, text);
            if (word != null) {
                words.add(word);
            }
        }
        return words;
    }

    private static List<TimedWord> approximateWordsFromSegments(JsonNode segments) {
        List<TimedWord> words = new ArrayList<>();
        for (JsonNode segment : segments) {
            double start = segment.path("start").asDouble(-1);
            double end = segment.path("end").asDouble(-1);
            String text = segment.path("text").asText("").strip();
            if (!Double.isFinite(start) || !Double.isFinite(end) || end <= start
                    || start < 0 || text.isEmpty()) {
                continue;
            }
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                double a = start + (end - start) * i / parts.length;
                double b = start + (end - start) * (i + 1) / parts.length;
                words.add(new TimedWord(toUs(a), toUs(b), parts[i]));
            }
        }
        return words;
    }

    private static TimedWord readTimedWord(JsonNode item, String text) {
        double start = item.path("start").asDouble(-1);
        double end = item.path("end").asDouble(-1);
        if (text.isEmpty() || !Double.isFinite(start) || !Double.isFinite(end)
                || start < 0 || end <= start) {
            return null;
        }
        return new TimedWord(toUs(start), toUs(end), text);
    }

    private static long toUs(double seconds) {
        return Math.round(seconds * 1_000_000.0);
    }

    private static List<SubtitleCue> groupWords(List<TimedWord> words) {
        List<Draft> drafts = new ArrayList<>();
        List<TimedWord> group = new ArrayList<>();

        for (TimedWord word : words) {
            if (!group.isEmpty()) {
                TimedWord first = group.getFirst();
                TimedWord previous = group.getLast();
                boolean longPause = word.startUs() - previous.endUs() > PAUSE_BREAK_US;
                boolean tooLong = word.endUs() - first.startUs() > MAX_CUE_US;
                boolean tooManyLetters = wrappedText(withWord(group, word)) == null;
                if (longPause || tooLong || tooManyLetters) {
                    addDraft(drafts, group);
                    group.clear();
                }
            }
            group.add(word);
            if (endsSentence(word.text()) && group.getLast().endUs() - group.getFirst().startUs()
                    >= ONE_SECOND_US) {
                addDraft(drafts, group);
                group.clear();
            }
        }
        addDraft(drafts, group);

        List<SubtitleCue> cues = new ArrayList<>();
        long previousEnd = 0;
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            long nextStart = i + 1 < drafts.size() ? drafts.get(i + 1).startUs() : Long.MAX_VALUE;
            long start = Math.max(previousEnd, draft.startUs());
            long desiredEnd = Math.max(draft.endUs(), start + ONE_SECOND_US);
            long end = Math.min(Math.min(desiredEnd, start + MAX_CUE_US), nextStart);
            if (end <= start) {
                continue; // Bad/overlapping word timestamps: keep the timeline valid.
            }
            cues.add(new SubtitleCue(start, end, draft.text()));
            previousEnd = end;
        }
        return cues;
    }

    private static List<TimedWord> withWord(List<TimedWord> words, TimedWord extra) {
        List<TimedWord> candidate = new ArrayList<>(words);
        candidate.add(extra);
        return candidate;
    }

    private static void addDraft(List<Draft> drafts, List<TimedWord> words) {
        if (words.isEmpty()) {
            return;
        }
        String text = wrappedText(words);
        if (text == null) {
            // Extremely long single tokens are unusual in transcription.
            // Fit a safe preview rather than throwing and losing all subtitles.
            text = words.getFirst().text();
            if (text.length() > 42) {
                text = text.substring(0, 42);
            }
        }
        drafts.add(new Draft(words.getFirst().startUs(), words.getLast().endUs(), text));
    }

    /** Wrap at words, maximum two lines and 42 characters per line. */
    private static String wrappedText(List<TimedWord> words) {
        List<StringBuilder> lines = new ArrayList<>();
        lines.add(new StringBuilder());
        for (TimedWord word : words) {
            String token = word.text();
            if (token.length() > 42) {
                return null;
            }
            StringBuilder line = lines.getLast();
            boolean punctuation = token.matches("^[,.!?;:]+$");
            String separator = line.isEmpty() || punctuation ? "" : " ";
            if (line.length() + separator.length() + token.length() > 42) {
                if (lines.size() == 2) {
                    return null;
                }
                line = new StringBuilder();
                lines.add(line);
                separator = "";
            }
            line.append(separator).append(token);
        }
        return String.join("\n", lines.stream().map(StringBuilder::toString).toList());
    }

    private static boolean endsSentence(String word) {
        return word.endsWith(".") || word.endsWith("!") || word.endsWith("?");
    }

    private record TimedWord(long startUs, long endUs, String text) { }
    private record Draft(long startUs, long endUs, String text) { }
}
