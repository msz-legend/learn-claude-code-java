package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** File tools constrained to one workspace root. */
public final class Workspace {
    private final Path root;

    public Workspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path candidate = root.resolve(path).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return candidate;
    }

    public String read(String path, Integer limit) throws IOException {
        Path file = resolve(path);
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String[] lines = text.split("\\R", -1);
        int count = lines.length;
        if (limit != null && limit > 0 && limit < count) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(lines[i]);
            }
            out.append("\n... (").append(count - limit).append(" more lines)");
            return out.toString();
        }
        return text;
    }

    public String write(String path, String content) throws IOException {
        Path file = resolve(path);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        return "Wrote " + (content == null ? 0 : content.length()) + " chars to " + path;
    }

    public String edit(String path, String oldText, String newText) throws IOException {
        if (oldText == null || oldText.isEmpty()) {
            return "Error: old_text is required";
        }
        Path file = resolve(path);
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int index = text.indexOf(oldText);
        if (index < 0) {
            return "Error: text not found in " + path;
        }
        String updated = text.substring(0, index) + (newText == null ? "" : newText)
                + text.substring(index + oldText.length());
        Files.writeString(file, updated, StandardCharsets.UTF_8);
        return "Edited " + path;
    }

    public String glob(String pattern) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern is required";
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .filter(path -> !isIgnored(path))
                    .filter(path -> matches(matcher, path, pattern))
                    .sorted(Comparator.comparing(path -> path.toString().replace('\\', '/')))
                    .forEach(path -> matches.add(path.toString().replace('\\', '/')));
        }
        if (matches.isEmpty()) {
            return "(no matches)";
        }
        int shown = Math.min(matches.size(), 200);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(matches.get(i));
        }
        if (matches.size() > shown) {
            out.append("\n... (more matches omitted; narrow the pattern)");
        }
        return out.toString();
    }

    private static boolean matches(PathMatcher matcher, Path path, String pattern) {
        if (matcher.matches(path)) {
            return true;
        }
        // Java's glob sometimes treats **/ as requiring at least one directory.
        if (pattern.startsWith("**/")) {
            PathMatcher shallow = FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3));
            return shallow.matches(Paths.get(path.getFileName().toString()));
        }
        return false;
    }

    private static boolean isIgnored(Path relative) {
        String text = relative.toString().replace('\\', '/');
        return text.equals(".git") || text.startsWith(".git/")
                || text.equals("out") || text.startsWith("out/")
                || text.equals(".tasks") || text.startsWith(".tasks/")
                || text.equals(".team") || text.startsWith(".team/")
                || text.equals(".workflows") || text.startsWith(".workflows/")
                || text.equals(".compact") || text.startsWith(".compact/");
    }
}
