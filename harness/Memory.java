package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * File-backed memory with three jobs:
 *   selection   - put relevant entries in front of the model
 *   extraction  - turn a finished conversation into a few durable facts
 *   consolidation - merge duplicates and keep the store small
 */
public final class Memory {
    public record Entry(String id, String text, List<String> tags, String createdAt) {
    }

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    public Memory(Path workspace) {
        this.file = workspace.resolve(".memory").resolve("memory.json");
        load();
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized String add(String text, List<String> tags) {
        String value = text == null ? "" : text.strip();
        if (value.length() < 3) {
            return "Error: memory text is too short";
        }
        for (Entry entry : entries) {
            if (entry.text().equalsIgnoreCase(value)) {
                return "Memory already exists: " + entry.id();
            }
        }
        Entry entry = new Entry(
                "mem-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6),
                value,
                tags == null ? List.of() : List.copyOf(tags),
                Instant.now().toString());
        entries.add(entry);
        while (entries.size() > 200) {
            entries.remove(0);
        }
        save();
        return "Remembered: " + value;
    }

    public synchronized List<Entry> search(String query, int limit) {
        int max = Math.max(1, Math.min(limit, 20));
        String text = query == null ? "" : query.strip();
        if (text.isEmpty()) {
            List<Entry> latest = new ArrayList<>(entries);
            latest.sort(Comparator.comparing(Entry::createdAt).reversed());
            return latest.subList(0, Math.min(max, latest.size()));
        }
        List<String> terms = terms(text);
        List<Scored> scored = new ArrayList<>();
        for (Entry entry : entries) {
            int score = score(entry, terms);
            if (score > 0) {
                scored.add(new Scored(entry, score));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(item -> item.entry().createdAt(), Comparator.reverseOrder()));
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(max, scored.size()); i++) {
            out.add(scored.get(i).entry());
        }
        return out;
    }

    public synchronized String renderForPrompt(String query, int limit) {
        List<Entry> found = search(query, limit);
        if (found.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("Relevant long-term memory:\n");
        for (Entry entry : found) {
            out.append("- ").append(entry.text());
            if (!entry.tags().isEmpty()) {
                out.append(" [").append(String.join(", ", entry.tags())).append(']');
            }
            out.append('\n');
        }
        return out.toString();
    }

    public synchronized String summary() {
        if (entries.isEmpty()) {
            return "(memory is empty)";
        }
        StringBuilder out = new StringBuilder("Memory (" + entries.size() + " entries):\n");
        int limit = Math.min(entries.size(), 30);
        for (int i = 0; i < limit; i++) {
            Entry entry = entries.get(i);
            out.append("- ").append(entry.text());
            if (!entry.tags().isEmpty()) {
                out.append(" [").append(String.join(", ", entry.tags())).append(']');
            }
            out.append('\n');
        }
        if (entries.size() > limit) {
            out.append("... (").append(entries.size() - limit).append(" more)");
        }
        return out.toString();
    }

    /** Ask the model to merge duplicates and drop low-value entries. */
    public synchronized String consolidate(LlmClient client) throws Exception {
        if (entries.size() < 2) {
            return summary();
        }
        List<Object> payload = new ArrayList<>();
        for (Entry entry : entries) {
            payload.add(Json.obj("text", entry.text(), "tags", entry.tags()));
        }
        String prompt = """
                Merge the memory entries below into a compact durable list.
                Rules:
                - merge entries that say the same thing
                - keep concrete project facts, preferences and decisions
                - drop temporary status, greetings and duplicated details
                - keep at most 50 entries
                Return a JSON array only, each item {"text":"...","tags":["..."]}.

                Entries:
                """ + Json.stringify(payload);
        Message.LlmResponse response = client.complete(
                "You maintain a coding agent's long-term memory. Return JSON only.",
                List.of(Message.user(prompt)),
                List.of());
        String text = extractJsonArray(response.text());
        if (text == null) {
            return "Error: model did not return a JSON array";
        }
        List<Object> items = Json.array(Json.parse(text));
        List<Entry> merged = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> map = Json.object(item);
            String value = Json.str(map, "text", "").strip();
            if (value.length() < 3) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            Object rawTags = map.get("tags");
            if (rawTags instanceof List<?> list) {
                for (Object tag : list) {
                    if (tag != null && !String.valueOf(tag).isBlank()) {
                        tags.add(String.valueOf(tag));
                    }
                }
            }
            merged.add(new Entry(
                    "mem-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6),
                    value,
                    tags,
                    Instant.now().toString()));
        }
        if (!merged.isEmpty()) {
            entries.clear();
            entries.addAll(merged.subList(0, Math.min(50, merged.size())));
            save();
        }
        return summary();
    }

    /** Extract zero to three durable facts from one completed turn. */
    public synchronized String extract(LlmClient client, String userText, String assistantText) throws Exception {
        String prompt = """
                Read one finished turn between a user and a coding agent.
                Extract at most three durable facts that will still matter in a later session.
                Do not store temporary progress, greetings or facts already present below.
                Return a JSON array only, each item {"text":"...","tags":["..."]}.
                Return [] when nothing is worth remembering.

                Existing memory:
                """ + summary() + """

                User:
                """ + userText + """

                Assistant:
                """ + assistantText;
        Message.LlmResponse response = client.complete(
                "You extract durable memory for a coding agent. Return JSON only.",
                List.of(Message.user(prompt)),
                List.of());
        String text = extractJsonArray(response.text());
        if (text == null) {
            return "Error: model did not return a JSON array";
        }
        List<Object> items = Json.array(Json.parse(text));
        int added = 0;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> map = Json.object(item);
            String value = Json.str(map, "text", "").strip();
            if (value.length() < 3) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            Object rawTags = map.get("tags");
            if (rawTags instanceof List<?> list) {
                for (Object tag : list) {
                    tags.add(String.valueOf(tag));
                }
            }
            boolean duplicate = entries.stream().anyMatch(entry -> entry.text().equalsIgnoreCase(value));
            if (!duplicate) {
                entries.add(new Entry(
                        "mem-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6),
                        value,
                        tags,
                        Instant.now().toString()));
                added++;
            }
        }
        if (added > 0) {
            while (entries.size() > 200) {
                entries.remove(0);
            }
            save();
        }
        return "Extracted " + added + " memory entries.";
    }

    public Tool writeTool() {
        return new Tool(
                "memory_write",
                "Store one durable fact for future sessions.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "text", Json.obj("type", "string"),
                                "tags", Json.obj("type", "array", "items", Json.obj("type", "string"))),
                        "required", Json.list("text")),
                input -> {
                    List<String> tags = new ArrayList<>();
                    Object raw = input.get("tags");
                    if (raw instanceof List<?> list) {
                        for (Object tag : list) {
                            tags.add(String.valueOf(tag));
                        }
                    } else if (raw instanceof String text && !text.isBlank()) {
                        tags.add(text);
                    }
                    return add(Json.str(input, "text", ""), tags);
                });
    }

    public Tool searchTool() {
        return new Tool(
                "memory_search",
                "Search durable memory by keyword.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "query", Json.obj("type", "string"),
                                "limit", Json.obj("type", "integer")),
                        "required", Json.list("query")),
                input -> {
                    List<Entry> found = search(
                            Json.str(input, "query", ""),
                            Json.integer(input, "limit", 5));
                    if (found.isEmpty()) {
                        return "(no matching memory)";
                    }
                    StringBuilder out = new StringBuilder();
                    for (Entry entry : found) {
                        out.append("- ").append(entry.text()).append('\n');
                    }
                    return out.toString();
                });
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Object root = Json.parse(text);
            if (!(root instanceof Map<?, ?>)) {
                return;
            }
            Map<String, Object> map = Json.object(root);
            Object raw = map.get("entries");
            if (!(raw instanceof List<?> list)) {
                return;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> entry = Json.object(item);
                List<String> tags = new ArrayList<>();
                if (entry.get("tags") instanceof List<?> tagList) {
                    for (Object tag : tagList) {
                        tags.add(String.valueOf(tag));
                    }
                }
                entries.add(new Entry(
                        Json.str(entry, "id", "mem-" + entries.size()),
                        Json.str(entry, "text", ""),
                        tags,
                        Json.str(entry, "createdAt", Instant.now().toString())));
            }
        } catch (Exception e) {
            System.err.println("[memory] could not load " + file + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            List<Object> items = new ArrayList<>();
            for (Entry entry : entries) {
                items.add(Json.obj(
                        "id", entry.id(),
                        "text", entry.text(),
                        "tags", entry.tags(),
                        "createdAt", entry.createdAt()));
            }
            Files.writeString(
                    file,
                    Json.stringify(Json.obj("version", 1, "entries", items)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[memory] could not save " + file + ": " + e.getMessage());
        }
    }

    private static List<String> terms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        String lower = query.toLowerCase(Locale.ROOT);
        for (String token : lower.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 1) {
                terms.add(token);
            }
        }
        // Chinese text often has no spaces; add character bigrams for recall.
        for (String token : new ArrayList<>(terms)) {
            if (token.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int i = 0; i + 2 <= token.length(); i++) {
                    terms.add(token.substring(i, i + 2));
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private static int score(Entry entry, List<String> terms) {
        String text = entry.text().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                score += term.length() >= 3 ? 3 : 1;
            }
            for (String tag : entry.tags()) {
                if (tag.toLowerCase(Locale.ROOT).contains(term)) {
                    score += 2;
                }
            }
        }
        return score;
    }

    private static String extractJsonArray(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private record Scored(Entry entry, int score) {
    }
}
