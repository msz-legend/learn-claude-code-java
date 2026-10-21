package s02_tool_use;

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
 * s02: Tool Use
 *
 * The loop from s01 does not change. The tool surface grows from one bash tool
 * to a dispatch table of five tools, and every file path is confined to the
 * workspace.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s02: Tool Use

                    Usage:
                      java -cp out s02_tool_use.Main

                    Tools: bash, read_file, write_file, edit_file, glob.
                    Requires ANTHROPIC_API_KEY or OPENAI_API_KEY (see .env.example).
                    """);
            return;
        }

        Cli.header("s02", "Tool Use");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        ToolRegistry tools = StandardTools.base(workspace, 120);

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Use the dedicated file tools instead of shell redirection. "
                        + "All paths are relative to the workspace.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s02", loop, history, query -> {
            if (!query.equals("/tools")) {
                return false;
            }
            tools.all().keySet().forEach(name -> System.out.println("- " + name));
            return true;
        });
    }
}
