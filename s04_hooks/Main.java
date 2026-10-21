package s04_hooks;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.Hooks;
import harness.LlmClient;
import harness.Message;
import harness.ShellHooks;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * s04: Hook System
 *
 * Hooks wrap the loop without editing it:
 *   userPromptSubmit -> observe the incoming turn
 *   preToolUse       -> block a call before it runs
 *   postToolUse      -> rewrite what the model sees
 *   stop             -> observe the final answer
 *
 * A JSON hook file can extend the built-in guards. See hooks.json.example.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s04: Hook System

                    Usage:
                      java -cp out s04_hooks.Main

                    Built-in hooks block writes to .env and tag shortened tool output.
                    Set HOOKS_FILE=hooks.json to add shell hooks.
                    """);
            return;
        }

        Cli.header("s04", "Hook System");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        ToolRegistry tools = StandardTools.base(workspace, 120);

        Hooks audit = new Hooks() {
            @Override
            public void userPromptSubmit(String prompt) {
                System.out.println(Cli.color("[hook] prompt: " + Cli.shorten(prompt, 100), Cli.DIM));
            }

            @Override
            public String preToolUse(Message.Block.ToolUse call) {
                if (call.name().equals("write_file") || call.name().equals("edit_file")) {
                    String path = String.valueOf(call.input().getOrDefault("path", ""))
                            .toLowerCase(Locale.ROOT);
                    if (path.endsWith(".env")) {
                        return "Writing .env is blocked by the secrets hook";
                    }
                }
                return null;
            }

            @Override
            public String postToolUse(Message.Block.ToolUse call, String output) {
                if (output.length() > 2_000) {
                    return output.substring(0, 2_000)
                            + "\n... [hook] output shortened; " + output.length() + " chars total";
                }
                return output;
            }

            @Override
            public void stop(List<Message.Msg> history) {
                System.out.println(Cli.color("[hook] turn ended with " + history.size() + " messages", Cli.DIM));
            }
        };

        Hooks hooks = audit;
        Path file = Path.of(Env.get("HOOKS_FILE", "hooks.json"));
        if (Files.isRegularFile(file)) {
            hooks = Hooks.compose(audit, ShellHooks.load(file, workspace.root()));
            System.out.println(Cli.color("[hook] loaded " + file.toAbsolutePath(), Cli.DIM));
        }

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Hooks may block or rewrite tool calls. Treat their messages as harness feedback.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .hooks(hooks)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s04", loop, history, null);
    }
}
