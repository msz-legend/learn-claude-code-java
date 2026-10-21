package harness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Persistent teammates over a shared task board.
 *
 * Coordination is deliberately file-based so several processes can take part:
 *   .team/mailboxes/<name>.jsonl   append-only mailbox
 *   .team/cursors/<name>.json      how far a reader has consumed
 *   .tasks/<id>.json               shared task graph (TaskStore)
 *   .worktrees/<name>              optional git worktree per teammate
 */
public final class Team {
    public record Member(String name, String role, String status) {
    }

    public record Mail(
            String id,
            String from,
            String to,
            String type,
            String content,
            String createdAt) {
    }

    @FunctionalInterface
    public interface ToolFactory {
        ToolRegistry create(Team team, Member member, Workspace workspace);
    }

    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");

    private final Path root;
    private final Path mailboxes;
    private final Path cursors;
    private final Path lockFile;
    private final Workspace workspace;
    private final TaskStore tasks;
    private final LlmClient client;
    private final Map<String, Member> members = new ConcurrentHashMap<>();
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private ToolFactory toolFactory;

    public Team(Workspace workspace, TaskStore tasks, LlmClient client) {
        this.workspace = workspace;
        this.tasks = tasks;
        this.client = client;
        this.root = workspace.root().resolve(".team");
        this.mailboxes = root.resolve("mailboxes");
        this.cursors = root.resolve("cursors");
        this.lockFile = root.resolve(".lock");
        try {
            Files.createDirectories(mailboxes);
            Files.createDirectories(cursors);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create team directory: " + e.getMessage(), e);
        }
        loadMembers();
    }

    public Team toolFactory(ToolFactory factory) {
        this.toolFactory = factory;
        return this;
    }

    public TaskStore tasks() {
        return tasks;
    }

    public synchronized String spawn(
            String name,
            String role,
            String prompt,
            boolean worktree,
            String taskId) {
        String memberName = name == null ? "" : name.strip();
        if (!NAME.matcher(memberName).matches()) {
            return "Error: name must be 1-32 letters, digits, _ or -";
        }
        if (memberName.equals("lead")) {
            return "Error: lead is reserved";
        }
        if (members.containsKey(memberName)) {
            return "Error: teammate already exists: " + memberName;
        }
        if (client == null || toolFactory == null) {
            return "Error: team is not configured with a model client and tool factory";
        }

        Path cwd = workspace.root();
        if (worktree) {
            String created = createWorktree(memberName);
            if (created.startsWith("Error:")) {
                return created;
            }
            cwd = Path.of(created.substring("Worktree: ".length()).strip());
        }
        if (taskId != null && !taskId.isBlank()) {
            tasks.setWorkdir(taskId, cwd.toString());
        }

        Member member = new Member(memberName, role == null ? "teammate" : role.strip(), "working");
        members.put(memberName, member);
        saveMembers();

        Workspace memberWorkspace = new Workspace(cwd);
        ToolRegistry tools = toolFactory.create(this, member, memberWorkspace);
        String system = """
                You are teammate %s (%s) in a small coding team at %s.
                You share a task board with the lead and other teammates.
                Use task_list and task_claim before working, and task_complete when done.
                Send progress or findings with team_send to "lead".
                Check team_inbox for messages. Stay inside your workspace.
                Act, don't explain.
                """.formatted(memberName, member.role(), cwd);

        List<Message.Msg> history = new ArrayList<>();
        history.add(Message.user(prompt == null || prompt.isBlank()
                ? "Start by listing the task board and claiming a ready task."
                : prompt));

        AgentLoop loop = new AgentLoop(client, tools, system)
                .maxTurns(Env.getInt("TEAMMATE_MAX_TURNS", 30))
                .notifications(() -> {
                    List<String> notes = new ArrayList<>();
                    for (Mail message : inboxMessages(memberName)) {
                        notes.add("[team " + message.from() + "] " + message.content());
                    }
                    return notes;
                })
                .listener(new AgentLoop.Listener() {
                    @Override
                    public void onToolStart(Message.Block.ToolUse call) {
                        System.out.println("[" + memberName + "] > " + call.name());
                    }

                    @Override
                    public void onToolResult(Message.Block.ToolUse call, String output, boolean error) {
                        if (error) {
                            System.out.println("[" + memberName + "] ! " + Cli.shorten(output, 240));
                        }
                    }
                });

        Thread thread = new Thread(() -> {
            try {
                String text = loop.run(history);
                send(memberName, "lead", "result",
                        text == null || text.isBlank() ? "(teammate finished without text)" : text);
                setStatus(memberName, "idle");
            } catch (Exception e) {
                send(memberName, "lead", "error", "Teammate failed: " + e.getMessage());
                setStatus(memberName, "offline");
            }
            threads.remove(memberName);
        }, "teammate-" + memberName);
        thread.setDaemon(true);
        threads.put(memberName, thread);
        thread.start();

        return "Spawned " + memberName + " (" + member.role() + ") in " + cwd;
    }

    public synchronized String list() {
        if (members.isEmpty()) {
            return "(no teammates)";
        }
        StringBuilder out = new StringBuilder();
        for (Member member : members.values()) {
            out.append("- ").append(member.name()).append(" [").append(member.status()).append("] ")
                    .append(member.role()).append('\n');
        }
        return out.toString().strip();
    }

    public List<Member> members() {
        return new ArrayList<>(members.values());
    }

    public String send(String from, String to, String type, String content) {
        String target = to == null || to.isBlank() ? "lead" : to.strip();
        if (!target.equals("lead") && !members.containsKey(target)) {
            return "Error: unknown recipient: " + target;
        }
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            return "Error: message content is required";
        }
        Mail message = new Mail(
                "msg-" + System.currentTimeMillis() + "-" + sequence.incrementAndGet(),
                from == null || from.isBlank() ? "lead" : from,
                target,
                type == null || type.isBlank() ? "message" : type,
                text,
                Instant.now().toString());
        try {
            return locked(() -> {
                try {
                    Path file = mailboxes.resolve(target + ".jsonl");
                    Files.writeString(
                            file,
                            Json.stringify(toJson(message)) + "\n",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                    return "Sent to " + target;
                } catch (IOException e) {
                    return "Error: could not append mailbox: " + e.getMessage();
                }
            });
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }

    public List<Mail> inboxMessages(String name) {
        String who = name == null || name.isBlank() ? "lead" : name.strip();
        Path file = mailboxes.resolve(who + ".jsonl");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return locked(() -> {
                try {
                    long size = Files.size(file);
                    long offset = readCursor(who);
                    if (offset > size) {
                        offset = 0;
                    }
                    byte[] bytes;
                    try (InputStream input = Files.newInputStream(file)) {
                        input.skipNBytes(offset);
                        bytes = input.readAllBytes();
                    }
                    if (bytes.length == 0) {
                        writeCursor(who, size);
                        return List.<Mail>of();
                    }
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    int lastNewline = text.lastIndexOf('\n');
                    if (lastNewline < 0) {
                        return List.<Mail>of();
                    }
                    String complete = text.substring(0, lastNewline + 1);
                    List<Mail> out = new ArrayList<>();
                    for (String line : complete.split("\\R")) {
                        if (line.isBlank()) {
                            continue;
                        }
                        try {
                            out.add(fromJson(Json.object(Json.parse(line))));
                        } catch (Exception ignored) {
                            // Skip a malformed line rather than losing the mailbox.
                        }
                    }
                    writeCursor(who, offset + complete.getBytes(StandardCharsets.UTF_8).length);
                    return out;
                } catch (IOException e) {
                    return List.<Mail>of();
                }
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public String inbox(String name) {
        List<Mail> messages = inboxMessages(name);
        if (messages.isEmpty()) {
            return "(no new messages)";
        }
        StringBuilder out = new StringBuilder();
        for (Mail message : messages) {
            out.append('[').append(message.from()).append(" / ").append(message.type()).append("] ")
                    .append(message.content()).append('\n');
        }
        return out.toString().strip();
    }

    public synchronized String shutdown(String name) {
        String target = name == null ? "" : name.strip();
        if (!members.containsKey(target)) {
            return "Error: unknown teammate: " + target;
        }
        Thread thread = threads.get(target);
        if (thread != null) {
            thread.interrupt();
            threads.remove(target);
        }
        setStatus(target, "offline");
        send("lead", target, "shutdown", "Please stop after your current step.");
        return "Requested shutdown of " + target;
    }

    public String createWorktree(String name) {
        Path repo = workspace.root();
        Shell.Result inside;
        try {
            inside = Shell.run("git rev-parse --is-inside-work-tree", repo, 20);
        } catch (Exception e) {
            return "Error: could not run git: " + e.getMessage();
        }
        if (inside.exitCode() != 0) {
            return "Error: workspace is not a git repository; cannot create a worktree";
        }
        Path dir = repo.resolve(".worktrees").resolve(name);
        String quoted = "\"" + dir.toString().replace("\"", "\\\"") + "\"";
        try {
            Files.createDirectories(dir.getParent());
            Shell.Result result = Shell.run("git worktree add -b task/" + name + " " + quoted, repo, 120);
            if (result.exitCode() != 0) {
                result = Shell.run("git worktree add " + quoted + " task/" + name, repo, 120);
            }
            if (result.exitCode() != 0) {
                return "Error: git worktree failed: " + result.output();
            }
            return "Worktree: " + dir;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String removeWorktree(String name) {
        Path dir = workspace.root().resolve(".worktrees").resolve(name);
        try {
            Shell.Result result = Shell.run(
                    "git worktree remove --force \"" + dir.toString().replace("\"", "\\\"") + "\"",
                    workspace.root(),
                    120);
            return result.exitCode() == 0 ? "Removed worktree " + dir : "Error: " + result.output();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public Tool spawnTool() {
        return new Tool(
                "team_spawn",
                "Start a persistent teammate with its own agent loop and mailbox.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "name", Json.obj("type", "string"),
                                "role", Json.obj("type", "string"),
                                "prompt", Json.obj("type", "string"),
                                "worktree", Json.obj("type", "boolean"),
                                "task_id", Json.obj("type", "string")),
                        "required", Json.list("name", "role", "prompt")),
                input -> spawn(
                        Json.str(input, "name", ""),
                        Json.str(input, "role", "teammate"),
                        Json.str(input, "prompt", ""),
                        Json.bool(input, "worktree", false),
                        Json.str(input, "task_id", "")));
    }

    public Tool listTool() {
        return new Tool(
                "team_list",
                "List teammates and their status.",
                Json.obj("type", "object", "properties", Json.obj()),
                input -> list());
    }

    public Tool sendTool(String from) {
        return new Tool(
                "team_send",
                "Send a message to the lead or another teammate.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "to", Json.obj("type", "string"),
                                "content", Json.obj("type", "string"),
                                "type", Json.obj("type", "string")),
                        "required", Json.list("to", "content")),
                input -> send(
                        from,
                        Json.str(input, "to", "lead"),
                        Json.str(input, "type", "message"),
                        Json.str(input, "content", "")));
    }

    public Tool inboxTool(String name) {
        return new Tool(
                "team_inbox",
                "Read new messages from your mailbox.",
                Json.obj("type", "object", "properties", Json.obj()),
                input -> inbox(name));
    }

    public Tool shutdownTool() {
        return new Tool(
                "team_shutdown",
                "Ask a teammate to stop.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("name", Json.obj("type", "string")),
                        "required", Json.list("name")),
                input -> shutdown(Json.str(input, "name", "")));
    }

    private void setStatus(String name, String status) {
        Member member = members.get(name);
        if (member != null) {
            members.put(name, new Member(member.name(), member.role(), status));
            saveMembers();
        }
    }

    private void loadMembers() {
        Path file = root.resolve("members.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Object> map = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            Object raw = map.get("members");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> entry = Json.object(item);
                    String name = Json.str(entry, "name", "");
                    if (!name.isBlank()) {
                        members.put(name, new Member(
                                name,
                                Json.str(entry, "role", "teammate"),
                                "offline"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[team] could not load members: " + e.getMessage());
        }
    }

    private void saveMembers() {
        try {
            Files.createDirectories(root);
            List<Object> items = new ArrayList<>();
            for (Member member : members.values()) {
                items.add(Json.obj("name", member.name(), "role", member.role(), "status", member.status()));
            }
            Path file = root.resolve("members.json");
            Path temp = root.resolve("members.json.tmp");
            Files.writeString(temp, Json.stringify(Json.obj("version", 1, "members", items)), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[team] could not save members: " + e.getMessage());
        }
    }

    private long readCursor(String name) {
        Path file = cursors.resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        try {
            Map<String, Object> map = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            Object value = map.get("offset");
            return value instanceof Number number ? number.longValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void writeCursor(String name, long offset) {
        try {
            Files.createDirectories(cursors);
            Files.writeString(
                    cursors.resolve(name + ".json"),
                    Json.stringify(Json.obj("offset", offset)),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // A lost cursor only causes duplicate delivery next time.
        }
    }

    private synchronized <T> T locked(ThrowingSupplier<T> action) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (true) {
            try {
                Files.createFile(lockFile);
                break;
            } catch (FileAlreadyExistsException e) {
                try {
                    if (System.currentTimeMillis() - Files.getLastModifiedTime(lockFile).toMillis() > 30_000) {
                        // A lock left behind by a crashed process would block
                        // every future run; a stale lock is safe to reclaim.
                        Files.deleteIfExists(lockFile);
                        continue;
                    }
                    if (System.currentTimeMillis() > deadline) {
                        throw new IllegalStateException("team mailbox is locked");
                    }
                    Thread.sleep(20);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for mailbox lock", interrupted);
                } catch (IOException io) {
                    throw new IllegalStateException("could not check mailbox lock: " + io.getMessage(), io);
                }
            } catch (IOException e) {
                throw new IllegalStateException("could not lock mailbox: " + e.getMessage(), e);
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
                // A leftover lock is reclaimed after 30 seconds (see above).
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static Map<String, Object> toJson(Mail message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", message.id());
        map.put("from", message.from());
        map.put("to", message.to());
        map.put("type", message.type());
        map.put("content", message.content());
        map.put("createdAt", message.createdAt());
        return map;
    }

    private static Mail fromJson(Map<String, Object> map) {
        return new Mail(
                Json.str(map, "id", ""),
                Json.str(map, "from", ""),
                Json.str(map, "to", ""),
                Json.str(map, "type", "message"),
                Json.str(map, "content", ""),
                Json.str(map, "createdAt", Instant.now().toString()));
    }
}
