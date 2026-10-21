package s10_task_system;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.TaskStore;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s10: Task System
 *
 * Tasks live on disk, one JSON file each. blockedBy forms a dependency graph.
 * claim() is atomic, so two agents cannot take the same ready task.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s10: Task System

                    Usage:
                      java -cp out s10_task_system.Main

                    Tools: task_create, task_list, task_get, task_update,
                           task_claim, task_complete. State lives in .tasks/.
                    """);
            return;
        }

        Cli.header("s10", "Task System");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        TaskStore tasks = new TaskStore(workspace.root());

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(tasks.createTool())
                .register(tasks.listTool())
                .register(tasks.getTool())
                .register(tasks.updateTool())
                .register(tasks.claimTool())
                .register(tasks.completeTool());

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Persist multi-step work as tasks. Create tasks with dependencies, "
                        + "claim one ready task at a time, and complete it with evidence. "
                        + "Use owner \"lead\" unless told otherwise.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s10", loop, history, query -> {
            if (!query.equals("/tasks")) {
                return false;
            }
            System.out.println(tasks.render());
            return true;
        });
    }
}
