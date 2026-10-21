package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A saved workflow owns the orchestration shape. The model supplies only a
 * workflow name; each agent call is journaled so a restart can reuse it.
 */
public final class Workflow {
    public record Meta(String name, String description, List<String> phases) {
    }

    @FunctionalInterface
    public interface AgentCall {
        String run(String prompt) throws Exception;
    }

    @FunctionalInterface
    public interface StepBody {
        String run(Context context) throws Exception;
    }

    public record Step(String name, StepBody body) {
    }

    public record StepResult(String step, String output, boolean cached, long millis) {
    }

    public record Result(
            String runId,
            String workflow,
            String status,
            Map<String, Object> output,
            List<StepResult> steps,
            List<String> events) {
        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("workflow ").append(workflow).append(" run=").append(runId)
                    .append(" status=").append(status).append('\n');
            for (StepResult step : steps) {
                out.append("  - ").append(step.step())
                        .append(step.cached() ? " (cached)" : " (" + step.millis() + " ms)")
                        .append('\n');
            }
            if (!events.isEmpty()) {
                out.append("events:\n");
                for (String event : events) {
                    out.append("  · ").append(event).append('\n');
                }
            }
            return out.toString().strip();
        }

        public String toJson() {
            List<Object> stepJson = new ArrayList<>();
            for (StepResult step : steps) {
                stepJson.add(Json.obj(
                        "step", step.step(),
                        "cached", step.cached(),
                        "ms", step.millis()));
            }
            return Json.stringify(Json.obj(
                    "runId", runId,
                    "workflow", workflow,
                    "status", status,
                    "output", output,
                    "steps", stepJson,
                    "events", events));
        }
    }

    public static final class Context {
        private final AgentCall agentCall;
        private final String stepName;
        private final Map<String, String> agentCache;
        private final Path journal;
        private final List<String> events;

        private Context(
                AgentCall agentCall,
                String stepName,
                Map<String, String> agentCache,
                Path journal,
                List<String> events) {
            this.agentCall = agentCall;
            this.stepName = stepName;
            this.agentCache = agentCache;
            this.journal = journal;
            this.events = events;
        }

        public String agent(String prompt) throws Exception {
            String key = stepName + ":" + Integer.toHexString(prompt.hashCode()) + ":" + prompt.length();
            String cached = agentCache.get(key);
            if (cached != null) {
                events.add("agent.cached " + stepName);
                return cached;
            }
            events.add("agent.start " + stepName);
            String output = agentCall.run(prompt);
            agentCache.put(key, output);
            append(journal, Json.obj(
                    "type", "agent",
                    "key", key,
                    "step", stepName,
                    "output", output,
                    "at", Instant.now().toString()));
            events.add("agent.done " + stepName);
            return output;
        }

        public List<String> parallel(List<String> prompts) throws Exception {
            if (prompts.isEmpty()) {
                return List.of();
            }
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, prompts.size()));
            try {
                List<Callable<String>> tasks = new ArrayList<>();
                for (String prompt : prompts) {
                    tasks.add(() -> agent(prompt));
                }
                List<Future<String>> futures = pool.invokeAll(tasks);
                List<String> out = new ArrayList<>();
                for (Future<String> future : futures) {
                    out.add(future.get());
                }
                return out;
            } finally {
                pool.shutdownNow();
            }
        }

        public void phase(String title) {
            events.add("phase " + title);
            System.out.println("[workflow] phase: " + title);
        }

        public void log(String message) {
            events.add("log " + message);
            System.out.println("[workflow] " + message);
        }

        private static void append(Path journal, Map<String, Object> event) {
            try {
                Files.createDirectories(journal.getParent());
                Files.writeString(
                        journal,
                        Json.stringify(event) + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("Could not append workflow journal: " + e.getMessage(), e);
            }
        }
    }

    public static final class Runner {
        private final Path root;
        private final Meta meta;
        private final List<Step> steps;
        private final AgentCall agentCall;

        public Runner(Path workspace, Meta meta, List<Step> steps, AgentCall agentCall) {
            this.root = workspace.resolve(".workflows");
            this.meta = meta;
            this.steps = List.copyOf(steps);
            this.agentCall = agentCall;
        }

        public Meta meta() {
            return meta;
        }

        public Result run(String runId) throws Exception {
            String id = runId == null || runId.isBlank()
                    ? "wf-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5)
                    : runId.strip();
            if (!id.matches("[a-zA-Z0-9_.-]{1,80}")) {
                throw new IllegalArgumentException("run id must be a safe 1-80 character slug");
            }
            Path dir = root.resolve(id);
            Files.createDirectories(dir);
            Path journal = dir.resolve("journal.jsonl");

            Map<String, String> completed = new LinkedHashMap<>();
            Map<String, String> agentCache = new LinkedHashMap<>();
            loadJournal(journal, completed, agentCache);

            List<StepResult> results = new ArrayList<>();
            List<String> events = new ArrayList<>();
            Map<String, Object> output = new LinkedHashMap<>();
            String status = "completed";

            for (Step step : steps) {
                String cached = completed.get(step.name());
                if (cached != null) {
                    results.add(new StepResult(step.name(), cached, true, 0));
                    output.put(step.name(), cached);
                    events.add("step.cached " + step.name());
                    continue;
                }
                long start = System.currentTimeMillis();
                events.add("step.start " + step.name());
                System.out.println("[workflow] step: " + step.name());
                try {
                    Context context = new Context(agentCall, step.name(), agentCache, journal, events);
                    String value = step.body().run(context);
                    long millis = System.currentTimeMillis() - start;
                    results.add(new StepResult(step.name(), value, false, millis));
                    output.put(step.name(), value);
                    Context.append(journal, Json.obj(
                            "type", "step",
                            "step", step.name(),
                            "status", "complete",
                            "output", value,
                            "ms", millis,
                            "at", Instant.now().toString()));
                    events.add("step.complete " + step.name());
                } catch (Exception e) {
                    long millis = System.currentTimeMillis() - start;
                    results.add(new StepResult(step.name(), "Error: " + e.getMessage(), false, millis));
                    output.put(step.name(), "Error: " + e.getMessage());
                    Context.append(journal, Json.obj(
                            "type", "step",
                            "step", step.name(),
                            "status", "error",
                            "error", String.valueOf(e.getMessage()),
                            "at", Instant.now().toString()));
                    events.add("step.error " + step.name());
                    status = "failed";
                    break;
                }
            }

            if (status.equals("completed")) {
                events.add("workflow.complete " + meta.name());
            }
            Result result = new Result(id, meta.name(), status, output, results, events);
            Files.writeString(dir.resolve("result.json"), result.toJson(), StandardCharsets.UTF_8);
            return result;
        }

        private static void loadJournal(
                Path journal,
                Map<String, String> completed,
                Map<String, String> agents) {
            if (!Files.isRegularFile(journal)) {
                return;
            }
            try {
                for (String line : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        Map<String, Object> event = Json.object(Json.parse(line));
                        String type = Json.str(event, "type", "");
                        if (type.equals("step") && Json.str(event, "status", "").equals("complete")) {
                            completed.put(Json.str(event, "step", ""), Json.str(event, "output", ""));
                        } else if (type.equals("agent")) {
                            agents.put(Json.str(event, "key", ""), Json.str(event, "output", ""));
                        }
                    } catch (RuntimeException ignored) {
                        // Ignore a torn last line.
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not read workflow journal: " + e.getMessage(), e);
            }
        }
    }

    public static final class Registry {
        private final Path workspace;
        private final AgentCall agentCall;
        private final Map<String, Runner> runners = new LinkedHashMap<>();

        public Registry(Path workspace, AgentCall agentCall) {
            this.workspace = workspace;
            this.agentCall = agentCall;
        }

        public Registry register(Meta meta, List<Step> steps) {
            validate(meta);
            runners.put(meta.name(), new Runner(workspace, meta, steps, agentCall));
            return this;
        }

        public Result run(String name, String resumeFromRunId) throws Exception {
            Runner runner = runners.get(name);
            if (runner == null) {
                throw new IllegalArgumentException("Unknown workflow: " + name
                        + " (available: " + String.join(", ", runners.keySet()) + ")");
            }
            return runner.run(resumeFromRunId);
        }

        public String renderCatalog() {
            if (runners.isEmpty()) {
                return "(no workflows)";
            }
            StringBuilder out = new StringBuilder();
            for (Runner runner : runners.values()) {
                out.append("- ").append(runner.meta().name()).append(": ")
                        .append(runner.meta().description()).append('\n');
            }
            return out.toString().strip();
        }

        public Tool tool() {
            return new Tool(
                    "workflow",
                    "Run a saved workflow. The host owns the orchestration; pass a workflow name.",
                    Json.obj(
                            "type", "object",
                            "properties", Json.obj(
                                    "name", Json.obj("type", "string"),
                                    "resume_from_run_id", Json.obj("type", "string")),
                            "required", Json.list("name")),
                    input -> {
                        String name = Json.str(input, "name", "");
                        Result result = run(name, Json.str(input, "resume_from_run_id", ""));
                        System.out.println(result.render());
                        return result.toJson();
                    });
        }

        private static void validate(Meta meta) {
            if (meta == null || meta.name() == null || meta.name().isBlank()) {
                throw new IllegalArgumentException("workflow meta.name is required");
            }
            if (!meta.name().matches("[a-zA-Z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("workflow meta.name must be a safe 1-64 character slug");
            }
            if (meta.description() == null || meta.description().isBlank()) {
                throw new IllegalArgumentException("workflow meta.description is required");
            }
        }
    }
}
