package s01_agent_loop;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s01: one loop + one tool.
 *
 * while (true):
 *   response = model(messages, tools)
 *   if no tool_use: return
 *   run tools, append tool_result, repeat
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s01: Agent Loop

                    Usage:
                      java -cp out s01_agent_loop.Main

                    Requires ANTHROPIC_API_KEY or OPENAI_API_KEY (see .env.example).
                    """);
            return;
        }

        Cli.header("s01", "Agent Loop");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();

        // The whole tool surface of s01: one shell.
        ToolRegistry tools = new ToolRegistry()
                .register(StandardTools.bash(workspace, 120));

        String system = StandardTools.systemPrompt(
                "a coding agent", workspace, "Use bash to solve tasks.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s01", loop, history, null);
    }
}
