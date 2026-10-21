package s09_memory;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Memory;
import harness.Message;
import harness.StandardTools;
import harness.Tool;
import harness.ToolRegistry;
import harness.Workspace;
import harness.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * s09: Memory System
 *
 * Three jobs around a small durable store:
 *   selection     - show relevant entries before a request
 *   extraction    - save durable facts from a finished turn
 *   consolidation - merge or prune entries when the store grows
 *
 * This chapter exposes them as tools so every step is visible.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s09: Memory System

                    Usage:
                      java -cp out s09_memory.Main

                    Tools: remember, recall, consolidate_memory.
                    Memory is stored in .memory/memory.json.
                    """);
            return;
        }

        Cli.header("s09", "Memory System");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        Memory memory = new Memory(workspace.root());

        Tool consolidate = new Tool(
                "consolidate_memory",
                "Merge duplicates and prune stale long-term memories.",
                Json.obj("type", "object", "properties", Json.obj()),
                input -> memory.consolidate(client));

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(memory.writeTool())
                .register(memory.searchTool())
                .register(consolidate);

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Save durable project facts with remember, look them up with recall, "
                        + "and consolidate when the store has duplicates.\n\n"
                        + memory.summary());

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s09", loop, history, query -> {
            if (query.startsWith("/recall ")) {
                String term = query.substring("/recall ".length()).strip();
                String rendered = memory.renderForPrompt(term, 5);
                System.out.println(rendered.isBlank() ? "(no matching memories)" : rendered);
                return true;
            }
            if (!query.equals("/memory")) {
                return false;
            }
            System.out.println(memory.summary());
            return true;
        });
    }
}
