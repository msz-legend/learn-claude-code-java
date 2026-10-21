package harness;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Slow commands run off the conversation thread. Their completion messages are
 * queued and injected as user messages before the next model request.
 */
public final class Background {
    public record Job(
            String id,
            String command,
            String status,
            String output,
            long startedAt,
            long finishedAt) {
    }

    private final Path cwd;
    private final ExecutorService pool;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final AtomicInteger counter = new AtomicInteger();

    public Background(Path cwd) {
        this.cwd = cwd;
        AtomicInteger threadNumber = new AtomicInteger();
        this.pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "background-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public String start(String command) {
        String text = command == null ? "" : command.strip();
        if (text.isEmpty()) {
            return "Error: command is required";
        }
        if (Permission.isDangerous(text)) {
            return "Error: Dangerous command blocked";
        }
        String id = "job-" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
        jobs.put(id, new Job(id, text, "running", "", System.currentTimeMillis(), 0));
        pool.submit(() -> {
            try {
                Shell.Result result = Shell.run(text, cwd, 600);
                String output = result.output();
                if (output.length() > 30_000) {
                    output = output.substring(0, 30_000) + "\n... (truncated)";
                }
                String status = result.timedOut() ? "timed_out"
                        : result.exitCode() == 0 ? "completed" : "failed";
                jobs.put(id, new Job(id, text, status, output, jobs.get(id).startedAt(), System.currentTimeMillis()));
            } catch (Exception e) {
                jobs.put(id, new Job(id, text, "failed", "Error: " + e.getMessage(),
                        jobs.get(id).startedAt(), System.currentTimeMillis()));
            }
        });
        return "Started " + id + " in the background: " + text;
    }

    public String check(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            return "Error: unknown job: " + id;
        }
        if (job.status().equals("running")) {
            return job.id() + " is still running (" + (System.currentTimeMillis() - job.startedAt()) + " ms)";
        }
        return job.id() + " " + job.status() + "\n" + job.output();
    }

    public List<Job> list() {
        return new ArrayList<>(jobs.values());
    }

    /** Messages that have completed since the last drain. */
    public List<String> drainNotifications() {
        List<String> out = new ArrayList<>();
        for (Job job : jobs.values()) {
            if (!job.status().equals("running") && reported.add(job.id())) {
                out.add("[background " + job.id() + " " + job.status() + "]\n" + job.output());
            }
        }
        return out;
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    public Tool runTool() {
        return new Tool(
                "background_run",
                "Run a slow shell command in the background. The result returns later.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("command", Json.obj("type", "string")),
                        "required", Json.list("command")),
                input -> start(Json.str(input, "command", "")));
    }

    public Tool checkTool() {
        return new Tool(
                "background_check",
                "Check one background job by id.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("id", Json.obj("type", "string")),
                        "required", Json.list("id")),
                input -> check(Json.str(input, "id", "")));
    }
}
