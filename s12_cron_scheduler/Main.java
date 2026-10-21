package s12_cron_scheduler;

import harness.AgentLoop;
import harness.Cli;
import harness.Cron;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s12: Cron Scheduler
 *
 * Jobs are persisted to .cron/jobs.json. A scheduler thread checks once a
 * minute; due prompts enter the next model request as harness notifications.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s12: Cron Scheduler

                    Usage:
                      java -cp out s12_cron_scheduler.Main

                    Tools: cron_create, cron_list, cron_cancel.
                    Expressions use five fields: minute hour day month weekday.
                    Jobs run while this process is open.
                    """);
            return;
        }

        Cli.header("s12", "Cron Scheduler");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        Cron cron = new Cron(workspace.root());
        cron.start();

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(cron.createTool())
                .register(cron.listTool())
                .register(cron.cancelTool());

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "You can schedule future prompts with cron_create. "
                        + "When a scheduled prompt arrives, treat it as a new user turn.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .notifications(() -> {
                    List<String> notes = new ArrayList<>();
                    for (Cron.Job job : cron.drainDue()) {
                        notes.add("[scheduled " + job.id() + " " + job.expression() + "] " + job.prompt());
                    }
                    return notes;
                })
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        try {
            Cli.repl("s12", loop, history, query -> {
                if (!query.equals("/cron")) {
                    return false;
                }
                System.out.println(cron.render());
                return true;
            });
        } finally {
            cron.stop();
        }
    }
}
