package s15_integrated_harness;

import harness.AgentLoop;
import harness.Agents;
import harness.Background;
import harness.Cli;
import harness.ContextCompactor;
import harness.Cron;
import harness.Env;
import harness.LlmClient;
import harness.Memory;
import harness.Message;
import harness.Skills;
import harness.StandardTools;
import harness.TaskStore;
import harness.Todo;
import harness.ToolRegistry;
import harness.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * s15: Integrated Harness
 *
 * Every mechanism from the course around the same loop:
 * file/shell tools, todo plans, skills, memory, tasks, background jobs,
 * scheduling, subagents and context compaction.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s15: Integrated Harness

                    Usage:
                      java -cp out s15_integrated_harness.Main

                    One loop, many harness ports. State is written under
                    .tasks/, .memory/, .cron/, .compact/ and skills/.
                    """);
            return;
        }

        Cli.header("s15", "Integrated Harness");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();

        Todo todo = new Todo();
        Skills skills = new Skills(Path.of(Env.get("SKILLS_DIR", "skills")));
        skills.catalog();
        Memory memory = new Memory(workspace.root());
        TaskStore tasks = new TaskStore(workspace.root());
        Background background = new Background(workspace.root());
        Cron cron = new Cron(workspace.root());
        ContextCompactor compactor = new ContextCompactor(workspace.root());

        ToolRegistry childTools = StandardTools.base(workspace, 120)
                .register(todo.tool())
                .register(skills.listTool())
                .register(skills.loadTool())
                .register(memory.writeTool())
                .register(memory.searchTool());

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(todo.tool())
                .register(skills.listTool())
                .register(skills.loadTool())
                .register(memory.writeTool())
                .register(memory.searchTool())
                .register(tasks.createTool())
                .register(tasks.listTool())
                .register(tasks.getTool())
                .register(tasks.updateTool())
                .register(tasks.claimTool())
                .register(tasks.completeTool())
                .register(background.runTool())
                .register(background.checkTool())
                .register(cron.createTool())
                .register(cron.listTool())
                .register(cron.cancelTool())
                .register(Agents.subagentTool(client, childTools,
                        StandardTools.systemPrompt("a focused subagent", workspace,
                                "Finish the assigned subtask and report evidence."),
                        Env.getInt("SUBAGENT_MAX_TURNS", 12)));

        cron.start();

        String system = StandardTools.systemPrompt(
                "a coding agent with a full harness",
                workspace,
                "Plan with todo_write. Reuse durable facts with recall. Track multi-step "
                        + "work on the task board. Run slow commands in the background. "
                        + "Schedule future prompts with cron_create. Delegate focused work to task.\n\n"
                        + skills.catalogText() + "\n\n" + memory.summary());

        AgentLoop loop = new AgentLoop(client, tools, system)
                .permission(PermissionDefaults.policy())
                .approver(Cli.consoleApprover())
                .compactor(compactor)
                .notifications(() -> {
                    List<String> notes = new ArrayList<>(background.drainNotifications());
                    for (Cron.Job job : cron.drainDue()) {
                        notes.add("[scheduled " + job.id() + "] " + job.prompt());
                    }
                    return notes;
                })
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        try {
            Cli.repl("s15", loop, history, query -> {
                if (query.equals("/status")) {
                    System.out.println("messages: " + history.size()
                            + "  tokens~" + ContextCompactor.estimateTokens(history)
                            + "  compactions: " + compactor.compactions());
                    System.out.println("jobs: " + background.list().size()
                            + "  tasks: " + tasks.list().size()
                            + "  memories: " + memory.entries().size()
                            + "  skills: " + skills.catalog().size());
                    return true;
                }
                if (query.equals("/tasks")) {
                    System.out.println(tasks.render());
                    return true;
                }
                return false;
            });
        } finally {
            cron.stop();
            background.shutdown();
        }
    }
}
