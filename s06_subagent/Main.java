package s06_subagent;

import harness.AgentLoop;
import harness.Agents;
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
 * s06: Subagent
 *
 * A subtask runs in a fresh message list and returns only its final text.
 * The parent context stays small; the child can still use every base tool.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s06: Subagent

                    Usage:
                      java -cp out s06_subagent.Main

                    The task tool starts a child loop with a fresh history.
                    SUBAGENT_MAX_TURNS controls the child's turn limit.
                    """);
            return;
        }

        Cli.header("s06", "Subagent");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        ToolRegistry childTools = StandardTools.base(workspace, 120);
        ToolRegistry tools = StandardTools.base(workspace, 120);

        String childSystem = StandardTools.systemPrompt(
                "a focused subagent",
                workspace,
                "Solve only the assigned subtask. Inspect files before changing them. "
                        + "Return a concise result with evidence.");

        tools.register(Agents.subagentTool(
                client,
                childTools,
                childSystem,
                Env.getInt("SUBAGENT_MAX_TURNS", 12)));

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Delegate self-contained investigation or implementation work to task. "
                        + "Give the subagent enough context to work without your history.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s06", loop, history, null);
    }
}
