package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Disk-backed task graph. One JSON file per task, plus a short-lived lock file
 * so several processes (or teammates) can claim work without overwriting each
 * other. Dependencies are ids in blockedBy.
 */
public final class TaskStore {
    public record Task(
            String id,
            String subject,
            String description,
            String status,
            List<String> blockedBy,
            String owner,
            String workdir,
            String createdAt,
            String updatedAt) {
    }

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path dir;
    private final Path lockFile;

    public TaskStore(Path workspace) {
        this.dir = workspace.resolve(".tasks");
        this.lockFile = dir.resolve(".lock");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create task directory: " + e.getMessage(), e);
        }
    }

    public Path directory() {
        return dir;
    }

    public synchronized String create(String subject, String description, List<String> blockedBy) {
        String title = subject == null ? "" : subject.strip();
        if (title.isEmpty()) {
            return "Error: subject is required";
        }
        return locked(() -> {
            String id = "task-" + LocalDateTime.now().format(ID_TIME) + "-"
                    + UUID.randomUUID().toString().substring(0, 4);
            Task task = new Task(
                    id,
                    title,
                    description == null ? "" : description.strip(),
                    "pending",
                    blockedBy == null ? List.of() : List.copyOf(blockedBy),
                    "",
                    "",
                    Instant.now().toString(),
                    Instant.now().toString());
            save(task);
            return "Created " + id + ": " + title;
        });
    }

    public synchronized String update(String id, List<String> addBlockedBy) {
        return locked(() -> {
            Task task = loadOrNull(id);
            if (task == null) {
                return "Error: task not found: " + id;
            }
            List<String> blocked = new ArrayList<>(task.blockedBy());
            for (String dependency : addBlockedBy == null ? List.<String>of() : addBlockedBy) {
                if (dependency == null || dependency.isBlank() || dependency.equals(id)) {
                    return "Error: invalid dependency: " + dependency;
                }
                if (dependsOn(dependency, id)) {
                    return "Error: dependency cycle: " + dependency + " already depends on " + id;
                }
                if (!blocked.contains(dependency)) {
                    blocked.add(dependency);
                }
            }
            Task updated = new Task(
                    task.id(), task.subject(), task.description(), task.status(), blocked,
                    task.owner(), task.workdir(), task.createdAt(), Instant.now().toString());
            save(updated);
            return "Updated " + id + " blockedBy=" + blocked;
        });
    }

    public Task get(String id) {
        return loadOrNull(id);
    }

    public List<Task> list() {
        List<Task> tasks = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            tasks.add(decode(Files.readString(path, StandardCharsets.UTF_8)));
                        } catch (Exception ignored) {
                            // A partially written file is not a task.
                        }
                    });
        } catch (IOException e) {
            return List.of();
        }
        tasks.sort(Comparator.comparing(Task::createdAt));
        return tasks;
    }

    public synchronized String claim(String id, String owner) {
        return locked(() -> {
            Task task = loadOrNull(id);
            if (task == null) {
                return "Error: task not found: " + id;
            }
            if (!task.status().equals("pending")) {
                return "Error: task " + id + " is " + task.status();
            }
            List<String> incomplete = incompleteDependencies(task);
            if (!incomplete.isEmpty()) {
                return "Error: blocked by " + String.join(", ", incomplete);
            }
            String who = owner == null || owner.isBlank() ? "agent" : owner.strip();
            for (Task other : list()) {
                if (other.owner().equals(who) && other.status().equals("in_progress")) {
                    return "Error: " + who + " already has " + other.id() + " in progress";
                }
            }
            Task claimed = new Task(
                    task.id(), task.subject(), task.description(), "in_progress", task.blockedBy(),
                    who, task.workdir(), task.createdAt(), Instant.now().toString());
            save(claimed);
            return "Claimed " + id + " for " + who;
        });
    }

    public synchronized String complete(String id, String owner) {
        return locked(() -> {
            Task task = loadOrNull(id);
            if (task == null) {
                return "Error: task not found: " + id;
            }
            if (!task.status().equals("in_progress")) {
                return "Error: task " + id + " is not in progress";
            }
            if (owner != null && !owner.isBlank() && !task.owner().equals(owner)) {
                return "Error: task " + id + " is owned by " + task.owner();
            }
            Task done = new Task(
                    task.id(), task.subject(), task.description(), "completed", task.blockedBy(),
                    task.owner(), task.workdir(), task.createdAt(), Instant.now().toString());
            save(done);
            return "Completed " + id;
        });
    }

    public synchronized String setWorkdir(String id, String workdir) {
        return locked(() -> {
            Task task = loadOrNull(id);
            if (task == null) {
                return "Error: task not found: " + id;
            }
            Task updated = new Task(
                    task.id(), task.subject(), task.description(), task.status(), task.blockedBy(),
                    task.owner(), workdir == null ? "" : workdir, task.createdAt(), Instant.now().toString());
            save(updated);
            return "Set " + id + " workdir=" + updated.workdir();
        });
    }

    public List<Task> ready() {
        List<Task> ready = new ArrayList<>();
        for (Task task : list()) {
            if (task.status().equals("pending") && incompleteDependencies(task).isEmpty()) {
                ready.add(task);
            }
        }
        return ready;
    }

    public String render() {
        List<Task> tasks = list();
        if (tasks.isEmpty()) {
            return "(no tasks)";
        }
        StringBuilder out = new StringBuilder();
        for (Task task : tasks) {
            out.append('[').append(task.status()).append("] ").append(task.id())
                    .append("  ").append(task.subject());
            if (!task.owner().isBlank()) {
                out.append("  @").append(task.owner());
            }
            if (!task.blockedBy().isEmpty()) {
                out.append("  blockedBy=").append(String.join(",", task.blockedBy()));
            }
            out.append('\n');
        }
        return out.toString().strip();
    }

    public Tool createTool() {
        return new Tool(
                "task_create",
                "Create a task in the shared task graph.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "subject", Json.obj("type", "string"),
                                "description", Json.obj("type", "string"),
                                "blocked_by", Json.obj(
                                        "type", "array",
                                        "items", Json.obj("type", "string"))),
                        "required", Json.list("subject")),
                input -> create(
                        Json.str(input, "subject", ""),
                        Json.str(input, "description", ""),
                        stringList(input.get("blocked_by"))));
    }

    public Tool listTool() {
        return new Tool(
                "task_list",
                "List tasks and their status.",
                Json.obj("type", "object", "properties", Json.obj()),
                input -> render());
    }

    public Tool getTool() {
        return new Tool(
                "task_get",
                "Read one task as JSON.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("id", Json.obj("type", "string")),
                        "required", Json.list("id")),
                input -> {
                    Task task = get(Json.str(input, "id", ""));
                    return task == null ? "Error: task not found" : Json.stringify(toJson(task));
                });
    }

    public Tool updateTool() {
        return new Tool(
                "task_update",
                "Add dependencies to a task.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "id", Json.obj("type", "string"),
                                "add_blocked_by", Json.obj(
                                        "type", "array",
                                        "items", Json.obj("type", "string"))),
                        "required", Json.list("id")),
                input -> update(
                        Json.str(input, "id", ""),
                        stringList(input.get("add_blocked_by"))));
    }

    public Tool claimTool() {
        return new Tool(
                "task_claim",
                "Claim a ready task. Only one in-progress task per owner.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "id", Json.obj("type", "string"),
                                "owner", Json.obj("type", "string")),
                        "required", Json.list("id")),
                input -> claim(
                        Json.str(input, "id", ""),
                        Json.str(input, "owner", "agent")));
    }

    public Tool completeTool() {
        return new Tool(
                "task_complete",
                "Mark a claimed task complete.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "id", Json.obj("type", "string"),
                                "owner", Json.obj("type", "string")),
                        "required", Json.list("id")),
                input -> complete(
                        Json.str(input, "id", ""),
                        Json.str(input, "owner", "agent")));
    }

    public static Map<String, Object> toJson(Task task) {
        return Json.obj(
                "id", task.id(),
                "subject", task.subject(),
                "description", task.description(),
                "status", task.status(),
                "blockedBy", task.blockedBy(),
                "owner", task.owner(),
                "workdir", task.workdir(),
                "createdAt", task.createdAt(),
                "updatedAt", task.updatedAt());
    }

    private List<String> incompleteDependencies(Task task) {
        List<String> incomplete = new ArrayList<>();
        for (String dependency : task.blockedBy()) {
            Task other = loadOrNull(dependency);
            if (other == null || !other.status().equals("completed")) {
                incomplete.add(dependency);
            }
        }
        return incomplete;
    }

    private boolean dependsOn(String taskId, String targetId) {
        Task task = loadOrNull(taskId);
        if (task == null) {
            return false;
        }
        for (String dependency : task.blockedBy()) {
            if (dependency.equals(targetId) || dependsOn(dependency, targetId)) {
                return true;
            }
        }
        return false;
    }

    private <T> T locked(ThrowingSupplier<T> action) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                Files.createFile(lockFile);
                break;
            } catch (FileAlreadyExistsException e) {
                try {
                    if (System.currentTimeMillis() - Files.getLastModifiedTime(lockFile).toMillis() > 30_000) {
                        Files.deleteIfExists(lockFile);
                        continue;
                    }
                    if (System.currentTimeMillis() > deadline) {
                        throw new IllegalStateException("Task store is locked by another process");
                    }
                    Thread.sleep(25);
                } catch (IOException | InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Could not acquire task lock", interrupted);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not create task lock: " + e.getMessage(), e);
            }
        }
        try {
            return action.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(lockFile);
            } catch (IOException ignored) {
                // The next process will clear a stale lock.
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private void save(Task task) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(task.id() + ".json");
            Path temp = dir.resolve(task.id() + ".json.tmp");
            Files.writeString(temp, Json.stringify(toJson(task)), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save task: " + e.getMessage(), e);
        }
    }

    private Task loadOrNull(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Path file = dir.resolve(id + ".json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return decode(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static Task decode(String text) {
        Map<String, Object> map = Json.object(Json.parse(text));
        return new Task(
                Json.str(map, "id", ""),
                Json.str(map, "subject", ""),
                Json.str(map, "description", ""),
                Json.str(map, "status", "pending"),
                stringList(map.get("blockedBy")),
                Json.str(map, "owner", ""),
                Json.str(map, "workdir", ""),
                Json.str(map, "createdAt", Instant.now().toString()),
                Json.str(map, "updatedAt", Instant.now().toString()));
    }

    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item));
                }
            }
        } else if (raw instanceof String text && !text.isBlank()) {
            out.add(text);
        }
        return out;
    }
}
