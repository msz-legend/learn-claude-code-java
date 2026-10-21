package s08_context_compact;

import harness.AgentLoop;
import harness.Cli;
import harness.ContextCompactor;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s08: Context Compact
 *
 * Four layers, cheapest first:
 *   1. archive oversized tool results to .compact/
 *   2. truncate old tool results
 *   3. summarize older turns and keep a recent window
 *   4. repeat truncation if the summary still does not fit
 *
 * Thresholds are environment variables so a demo can trigger compaction
 * without filling a real context window.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s08: Context Compact

                    Usage:
                      java -cp out s08_context_compact.Main

                    Tuning:
                      COMPACT_TOOL_RESULT_BUDGET   total chars of tool results (default 200000)
                      COMPACT_SUMMARIZE_AT_TOKENS  summary threshold (default 24000)
                      COMPACT_KEEP_MESSAGES        recent messages to keep (default 8)

                    Try: COMPACT_SUMMARIZE_AT_TOKENS=1500 COMPACT_KEEP_MESSAGES=2
                    """);
            return;
        }

        Cli.header("s08", "Context Compact");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        ContextCompactor compactor = new ContextCompactor(workspace.root());
        ToolRegistry tools = StandardTools.base(workspace, 120);

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Keep working across compactions. After a summary, re-read files if you need exact text.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .compactor(compactor)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s08", loop, history, query -> {
            if (!query.equals("/context")) {
                return false;
            }
            System.out.println("messages: " + history.size());
            System.out.println("estimated tokens: " + ContextCompactor.estimateTokens(history));
            System.out.println("compactions: " + compactor.compactions());
            return true;
        });
    }
}
