package harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offline checks for the parts that do not need a model call.
 *
 * Run: java -cp out harness.SelfTest
 */
public final class SelfTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testJson();
        testPermission();
        testTodo();
        testCron();
        testTasks();
        testCompactionEstimate();
        testMemory();
        testShell();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testJson() {
        String raw = "{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":-2.5}}";
        Map<String, Object> value = Json.object(Json.parse(raw));
        check("json number", Json.integer(value, "a", 0) == 1);
        check("json bool", Json.bool(value, "b", false) == false);
        check("json nested", Json.array(value.get("b")).size() == 3);
        check("json double", Json.stringify(Json.parse(raw)).contains("-2.5"));
        check("json string escape", Json.stringify(Json.obj("t", "a\"b\nc")).contains("\\\""));
    }

    private static void testPermission() {
        check("dangerous rm", Permission.isDangerous("rm -rf /"));
        check("safe ls", !Permission.isDangerous("ls -la"));
        Permission.Policy policy = Permission.rules(Permission.defaultRules());
        Message.Block.ToolUse safe = new Message.Block.ToolUse("1", "bash", Json.obj("command", "ls"));
        Message.Block.ToolUse ask = new Message.Block.ToolUse("2", "bash", Json.obj("command", "rm notes.txt"));
        check("allow safe", policy.check(safe, null).mode() == Permission.Mode.ALLOW);
        check("ask delete", policy.check(ask, null).mode() == Permission.Mode.ASK);
    }

    private static void testTodo() {
        Todo todo = new Todo();
        todo.write(Json.list(
                Json.obj("content", "read code", "status", "completed"),
                Json.obj("content", "write test", "status", "in_progress")));
        check("todo count", todo.items().size() == 2);
        check("todo render", todo.render().contains("[>] write test"));
        String error = todo.write(Json.list(
                Json.obj("content", "a", "status", "in_progress"),
                Json.obj("content", "b", "status", "in_progress")));
        check("todo one in progress", error.startsWith("Error:"));
    }

    private static void testCron() {
        check("cron every minute", Cron.Expression.matches("* * * * *", ZonedDateTime.now()));
        check("cron bad field", Cron.Expression.validate("* * *") != null);
        check("cron step", Cron.Expression.matches("*/5 * * * *",
                ZonedDateTime.now().withMinute(10)));
        check("cron no match", !Cron.Expression.matches("0 0 1 1 *",
                ZonedDateTime.now().withMonth(6)));
    }

    private static void testTasks() throws Exception {
        Path dir = Files.createTempDirectory("lcc-java-tasks");
        try {
            TaskStore store = new TaskStore(dir);
            String first = store.create("first", "", List.of());
            String id = first.substring("Created ".length(), first.indexOf(':'));
            check("task created", store.get(id) != null);
            check("task ready", store.ready().size() == 1);
            String second = store.create("second", "", List.of(id));
            String secondId = second.substring("Created ".length(), second.indexOf(':'));
            check("task blocked", store.ready().size() == 1);
            check("task claim dep blocked", store.claim(secondId, "me").startsWith("Error:"));
            check("task claim", store.claim(id, "me").startsWith("Claimed"));
            check("task complete", store.complete(id, "me").startsWith("Completed"));
            check("task unblocked", store.ready().size() == 1);
            check("task cycle", store.update(id, List.of(secondId)).startsWith("Error:"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void testCompactionEstimate() {
        List<Message.Msg> history = new ArrayList<>();
        history.add(Message.user("x".repeat(400)));
        check("token estimate", ContextCompactor.estimateTokens(history) >= 100);
        check("transcript", ContextCompactor.transcript(history, 1000).contains("[user]"));
    }

    private static void testMemory() throws Exception {
        Path dir = Files.createTempDirectory("lcc-java-memory");
        try {
            Memory memory = new Memory(dir);
            memory.add("The build command is javac -d out", List.of("build"));
            check("memory add", memory.entries().size() == 1);
            check("memory search", memory.search("javac", 5).size() == 1);
            check("memory prompt", memory.renderForPrompt("build", 5).contains("javac"));
            check("memory dedupe", memory.add("The build command is javac -d out", List.of()).startsWith("Memory already exists"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void testShell() throws Exception {
        Shell.Result result = Shell.run(Env.isWindows() ? "echo hello" : "printf hello",
                Path.of(".").toAbsolutePath(), 20);
        check("shell output", result.output().toLowerCase().contains("hello"));
        check("shell exit", result.exitCode() == 0);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("ok   " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    private static void deleteTree(Path root) {
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best effort temp cleanup.
                        }
                    });
        } catch (Exception ignored) {
            // Best effort temp cleanup.
        }
    }
}
