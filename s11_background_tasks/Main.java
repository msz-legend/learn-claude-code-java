package s11_background_tasks;

import harness.AgentLoop;
import harness.Background;
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
 * s11: Background Tasks
 *
 * Slow commands run off the conversation thread. When a job finishes, its
 * output is queued and injected as a user message before the next request.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s11: Background Tasks

                    Usage:
                      java -cp out s11_background_tasks.Main

                    Tools: background_run, background_check. Completed jobs are
                    announced automatically before the next model call.
                    """);
            return;
        }

        Cli.header("s11", "Background Tasks");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        Background background = new Background(workspace.root());

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(background.runTool())
                .register(background.checkTool());

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Use background_run for slow commands (builds, test suites, downloads). "
                        + "Keep working on something else, then check the job or wait for its notification.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .notifications(background::drainNotifications)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        try {
            Cli.repl("s11", loop, history, query -> {
                if (!query.equals("/jobs")) {
                    return false;
                }
                List<Background.Job> jobs = background.list();
                if (jobs.isEmpty()) {
                    System.out.println("(no background jobs)");
                }
                for (Background.Job job : jobs) {
                    System.out.println("- " + job.id() + " [" + job.status() + "] " + job.command());
                }
                return true;
            });
        } finally {
            background.shutdown();
        }
    }
}
