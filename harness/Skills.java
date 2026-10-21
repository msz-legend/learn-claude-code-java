package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A skill is a folder with a SKILL.md file. The catalog exposes only name and
 * description; the body is loaded when the model asks for it.
 */
public final class Skills {
    public record Skill(String name, String description, Path path) {
    }

    private final Path root;
    private final Map<String, Skill> byName = new LinkedHashMap<>();

    public Skills(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public synchronized List<Skill> catalog() {
        byName.clear();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Skill> skills = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .forEach(path -> {
                        Skill skill = parse(path);
                        if (skill != null) {
                            skills.add(skill);
                            byName.put(skill.name(), skill);
                        }
                    });
        } catch (IOException e) {
            return List.of();
        }
        skills.sort(Comparator.comparing(Skill::name));
        return skills;
    }

    public synchronized String catalogText() {
        List<Skill> skills = catalog();
        if (skills.isEmpty()) {
            return "(no skills found)";
        }
        StringBuilder out = new StringBuilder("Available skills:\n");
        for (Skill skill : skills) {
            out.append("- ").append(skill.name()).append(": ")
                    .append(skill.description()).append('\n');
        }
        out.append("\nCall load_skill with a skill name to read its instructions.");
        return out.toString();
    }

    public synchronized String load(String name) {
        if (byName.isEmpty()) {
            catalog();
        }
        Skill skill = byName.get(name);
        if (skill == null) {
            return "Error: unknown skill: " + name;
        }
        try {
            String text = Files.readString(skill.path(), StandardCharsets.UTF_8);
            return text.length() > 60_000 ? text.substring(0, 60_000) + "\n... (truncated)" : text;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public Tool listTool() {
        return new Tool(
                "list_skills",
                "List the skills available on this machine.",
                Json.obj("type", "object", "properties", Json.obj()),
                input -> catalogText());
    }

    public Tool loadTool() {
        return new Tool(
                "load_skill",
                "Load the full instructions for one skill.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("name", Json.obj("type", "string")),
                        "required", Json.list("name")),
                input -> load(Json.str(input, "name", "")));
    }

    private Skill parse(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, String> front = frontmatter(text);
            String name = front.getOrDefault("name", path.getParent().getFileName().toString()).strip();
            String description = front.getOrDefault("description", "").strip();
            if (description.isEmpty()) {
                description = firstBodyLine(text);
            }
            if (description.length() > 300) {
                description = description.substring(0, 300) + "...";
            }
            return new Skill(name, description, path);
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, String> frontmatter(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!text.startsWith("---")) {
            return map;
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            return map;
        }
        String block = text.substring(3, end);
        for (String line : block.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    private static String firstBodyLine(String text) {
        boolean inFront = false;
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.equals("---")) {
                inFront = !inFront;
                continue;
            }
            if (!inFront && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed;
            }
        }
        return "(no description)";
    }
}
