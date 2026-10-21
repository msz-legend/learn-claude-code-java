package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Durable five-field cron scheduling. The scheduler writes due jobs to an
 * in-memory queue; the host decides when to turn that queue into a user turn.
 */
public final class Cron {
    public record Job(
            String id,
            String expression,
            String prompt,
            boolean recurring,
            boolean enabled,
            String lastRunKey,
            String createdAt) {
    }

    private final Path file;
    private final List<Job> jobs = new ArrayList<>();
    private final List<Job> due = new ArrayList<>();
    private Thread thread;
    private volatile boolean running;

    public Cron(Path workspace) {
        this.file = workspace.resolve(".cron").resolve("jobs.json");
        load();
    }

    public synchronized String schedule(String expression, String prompt, boolean recurring) {
        String error = Expression.validate(expression);
        if (error != null) {
            return "Error: " + error;
        }
        String text = prompt == null ? "" : prompt.strip();
        if (text.isEmpty()) {
            return "Error: prompt is required";
        }
        Job job = new Job(
                "cron-" + System.currentTimeMillis() + "-" + (jobs.size() + 1),
                expression.strip(),
                text,
                recurring,
                true,
                "",
                Instant.now().toString());
        jobs.add(job);
        save();
        return "Scheduled " + job.id() + " [" + job.expression() + "] " + text;
    }

    public synchronized String cancel(String id) {
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).id().equals(id)) {
                Job removed = jobs.remove(i);
                save();
                return "Cancelled " + removed.id();
            }
        }
        return "Error: unknown job: " + id;
    }

    public synchronized String render() {
        if (jobs.isEmpty()) {
            return "(no scheduled jobs)";
        }
        StringBuilder out = new StringBuilder();
        for (Job job : jobs) {
            out.append(job.enabled() ? "[on] " : "[off] ")
                    .append(job.id()).append("  ").append(job.expression())
                    .append("  ").append(job.prompt());
            if (!job.lastRunKey().isBlank()) {
                out.append("  last=").append(job.lastRunKey());
            }
            out.append('\n');
        }
        return out.toString().strip();
    }

    public synchronized List<Job> jobs() {
        return List.copyOf(jobs);
    }

    public synchronized List<Job> drainDue() {
        List<Job> out = new ArrayList<>(due);
        due.clear();
        return out;
    }

    public synchronized void poll(ZonedDateTime now) {
        String minuteKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        boolean changed = false;
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (!job.enabled() || minuteKey.equals(job.lastRunKey())) {
                continue;
            }
            if (Expression.matches(job.expression(), now)) {
                due.add(job);
                Job updated = new Job(
                        job.id(), job.expression(), job.prompt(), job.recurring(),
                        job.recurring(), minuteKey, job.createdAt());
                jobs.set(i, updated);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(() -> {
            while (running) {
                try {
                    poll(ZonedDateTime.now());
                    Thread.sleep(15_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[cron] " + e.getMessage());
                }
            }
        }, "cron-scheduler");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public Tool createTool() {
        return new Tool(
                "cron_create",
                "Schedule a prompt with a five-field cron expression.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "cron", Json.obj("type", "string"),
                                "prompt", Json.obj("type", "string"),
                                "recurring", Json.obj("type", "boolean")),
                        "required", Json.list("cron", "prompt")),
                input -> schedule(
                        Json.str(input, "cron", ""),
                        Json.str(input, "prompt", ""),
                        Json.bool(input, "recurring", true)));
    }

    public Tool listTool() {
        return new Tool(
                "cron_list",
                "List scheduled jobs.",
                Json.obj("type", "object", "properties", Json.obj()),
                input -> render());
    }

    public Tool cancelTool() {
        return new Tool(
                "cron_cancel",
                "Cancel a scheduled job by id.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("id", Json.obj("type", "string")),
                        "required", Json.list("id")),
                input -> cancel(Json.str(input, "id", "")));
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Object> root = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            Object raw = root.get("jobs");
            if (!(raw instanceof List<?> list)) {
                return;
            }
            for (Object item : list) {
                Map<String, Object> map = Json.object(item);
                jobs.add(new Job(
                        Json.str(map, "id", ""),
                        Json.str(map, "expression", "* * * * *"),
                        Json.str(map, "prompt", ""),
                        Json.bool(map, "recurring", true),
                        Json.bool(map, "enabled", true),
                        Json.str(map, "lastRunKey", ""),
                        Json.str(map, "createdAt", Instant.now().toString())));
            }
        } catch (Exception e) {
            System.err.println("[cron] could not load " + file + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            List<Object> items = new ArrayList<>();
            for (Job job : jobs) {
                items.add(Json.obj(
                        "id", job.id(),
                        "expression", job.expression(),
                        "prompt", job.prompt(),
                        "recurring", job.recurring(),
                        "enabled", job.enabled(),
                        "lastRunKey", job.lastRunKey(),
                        "createdAt", job.createdAt()));
            }
            Path temp = file.resolveSibling("jobs.json.tmp");
            Files.writeString(temp, Json.stringify(Json.obj("version", 1, "jobs", items)), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[cron] could not save " + file + ": " + e.getMessage());
        }
    }

    /** Five-field cron: minute hour day-of-month month day-of-week. */
    public static final class Expression {
        private Expression() {
        }

        public static String validate(String expression) {
            if (expression == null || expression.isBlank()) {
                return "cron expression is required";
            }
            String[] fields = expression.strip().split("\\s+");
            if (fields.length != 5) {
                return "expected 5 fields: minute hour day month weekday";
            }
            int[][] ranges = {{0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 7}};
            String[] names = {"minute", "hour", "day", "month", "weekday"};
            for (int i = 0; i < 5; i++) {
                String error = validateField(fields[i], ranges[i][0], ranges[i][1]);
                if (error != null) {
                    return names[i] + ": " + error;
                }
            }
            return null;
        }

        public static boolean matches(String expression, ZonedDateTime now) {
            if (validate(expression) != null) {
                return false;
            }
            String[] fields = expression.strip().split("\\s+");
            if (!fieldMatches(fields[0], now.getMinute(), 0, 59)
                    || !fieldMatches(fields[1], now.getHour(), 0, 23)
                    || !fieldMatches(fields[3], now.getMonthValue(), 1, 12)) {
                return false;
            }
            boolean dayRestricted = !fields[2].equals("*") && !fields[2].equals("?");
            boolean weekdayRestricted = !fields[4].equals("*") && !fields[4].equals("?");
            int cronWeekday = now.getDayOfWeek().getValue() % 7;
            boolean dayMatch = fieldMatches(fields[2], now.getDayOfMonth(), 1, 31);
            boolean weekdayMatch = fieldMatches(fields[4], cronWeekday, 0, 7);
            if (dayRestricted && weekdayRestricted) {
                return dayMatch || weekdayMatch;
            }
            return dayMatch && weekdayMatch;
        }

        private static String validateField(String field, int min, int max) {
            for (String part : field.split(",")) {
                String value = part.strip();
                if (value.isEmpty()) {
                    return "empty list item";
                }
                int step = 1;
                int slash = value.indexOf('/');
                if (slash >= 0) {
                    try {
                        step = Integer.parseInt(value.substring(slash + 1));
                    } catch (NumberFormatException e) {
                        return "bad step in " + value;
                    }
                    if (step <= 0) {
                        return "step must be positive";
                    }
                    value = value.substring(0, slash);
                }
                int low;
                int high;
                if (value.equals("*") || value.equals("?")) {
                    low = min;
                    high = max;
                } else if (value.contains("-")) {
                    String[] pair = value.split("-", -1);
                    if (pair.length != 2) {
                        return "bad range " + value;
                    }
                    try {
                        low = Integer.parseInt(pair[0]);
                        high = Integer.parseInt(pair[1]);
                    } catch (NumberFormatException e) {
                        return "bad range " + value;
                    }
                } else {
                    try {
                        low = Integer.parseInt(value);
                        high = low;
                    } catch (NumberFormatException e) {
                        return "bad value " + value;
                    }
                }
                if (low < min || high > max || low > high) {
                    return value + " is outside " + min + "-" + max;
                }
            }
            return null;
        }

        private static boolean fieldMatches(String field, int value, int min, int max) {
            for (String part : field.split(",")) {
                String token = part.strip();
                int step = 1;
                int slash = token.indexOf('/');
                if (slash >= 0) {
                    step = Integer.parseInt(token.substring(slash + 1));
                    token = token.substring(0, slash);
                }
                int low;
                int high;
                if (token.equals("*") || token.equals("?")) {
                    low = min;
                    high = max;
                } else if (token.contains("-")) {
                    String[] pair = token.split("-", -1);
                    low = Integer.parseInt(pair[0]);
                    high = Integer.parseInt(pair[1]);
                } else {
                    low = Integer.parseInt(token);
                    high = low;
                }
                for (int candidate = low; candidate <= high; candidate += step) {
                    if (candidate == value) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
