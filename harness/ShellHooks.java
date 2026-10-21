package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Host-owned hook commands loaded from hooks.json.
 *
 * This is configuration, not model input. A hook runs through the same shell as
 * the bash tool, so only load files you trust.
 */
public final class ShellHooks implements Hooks {
    private final Path cwd;
    private final Map<String, List<Hook>> hooks;

    private ShellHooks(Path cwd, Map<String, List<Hook>> hooks) {
        this.cwd = cwd;
        this.hooks = hooks;
    }

    public record Hook(String matcher, String command, int timeoutSeconds) {
    }

    public static ShellHooks load(Path file, Path cwd) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new ShellHooks(cwd, Map.of());
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> root = Json.object(Json.parse(text));
        Map<String, List<Hook>> parsed = new LinkedHashMap<>();
        for (String event : List.of("UserPromptSubmit", "PreToolUse", "PostToolUse", "Stop")) {
            List<Hook> list = new ArrayList<>();
            Object raw = root.get(event);
            if (raw instanceof List<?> items) {
                for (Object item : items) {
                    Map<String, Object> map = Json.object(item);
                    String command = Json.str(map, "command", "");
                    if (command.isBlank()) {
                        continue;
                    }
                    list.add(new Hook(
                            Json.str(map, "matcher", "*"),
                            command,
                            Json.integer(map, "timeout", 30)));
                }
            }
            parsed.put(event, list);
        }
        return new ShellHooks(cwd, parsed);
    }

    @Override
    public void userPromptSubmit(String prompt) throws Exception {
        runEvent("UserPromptSubmit", null, Map.of("PROMPT", prompt));
    }

    @Override
    public String preToolUse(Message.Block.ToolUse call) throws Exception {
        for (Hook hook : hooks.getOrDefault("PreToolUse", List.of())) {
            if (!matches(hook.matcher(), call.name())) {
                continue;
            }
            Shell.Result result = run(hook, Map.of(
                    "TOOL_NAME", call.name(),
                    "TOOL_INPUT", Json.stringify(call.input())));
            if (result.exitCode() != 0) {
                return "Hook blocked " + call.name() + ": " + result.output();
            }
        }
        return null;
    }

    @Override
    public String postToolUse(Message.Block.ToolUse call, String output) throws Exception {
        String current = output;
        for (Hook hook : hooks.getOrDefault("PostToolUse", List.of())) {
            if (!matches(hook.matcher(), call.name())) {
                continue;
            }
            Shell.Result result = run(hook, Map.of(
                    "TOOL_NAME", call.name(),
                    "TOOL_INPUT", Json.stringify(call.input()),
                    "TOOL_OUTPUT", current));
            if (result.exitCode() != 0) {
                current = current + "\n[post hook failed: " + result.output() + "]";
            } else if (!result.output().isBlank()) {
                System.out.println("[hook] " + result.output());
            }
        }
        return current;
    }

    @Override
    public void stop(List<Message.Msg> history) throws Exception {
        String text = history.isEmpty() ? "" : history.get(history.size() - 1).text();
        runEvent("Stop", null, Map.of("STOP_TEXT", text));
    }

    private void runEvent(String event, Message.Block.ToolUse call, Map<String, String> extra)
            throws IOException, InterruptedException {
        for (Hook hook : hooks.getOrDefault(event, List.of())) {
            if (call != null && !matches(hook.matcher(), call.name())) {
                continue;
            }
            Shell.Result result = run(hook, extra);
            if (!result.output().isBlank()) {
                System.out.println("[hook:" + event + "] " + result.output());
            }
        }
    }

    private Shell.Result run(Hook hook, Map<String, String> extra) throws IOException, InterruptedException {
        Map<String, String> env = new LinkedHashMap<>(extra);
        env.put("HOOK_COMMAND", hook.command());
        return Shell.run(hook.command(), cwd, hook.timeoutSeconds(), env);
    }

    private static boolean matches(String matcher, String toolName) {
        if (matcher == null || matcher.isBlank() || "*".equals(matcher)) {
            return true;
        }
        String value = matcher.toLowerCase(Locale.ROOT);
        String name = toolName.toLowerCase(Locale.ROOT);
        if (value.endsWith("*")) {
            return name.startsWith(value.substring(0, value.length() - 1));
        }
        return name.equals(value);
    }
}
