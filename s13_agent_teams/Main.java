package s13_agent_teams;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.TaskStore;
import harness.Team;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s13: Agent Teams
 *
 * The lead and its teammates share a disk-backed task board and append-only
 * mailboxes. A spawn can bind a teammate to a task and an optional worktree.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s13: Agent Teams

                    Usage:
                      java -cp out s13_agent_teams.Main

                    Lead tools: team_spawn, team_list, team_send, team_inbox,
                    team_shutdown plus task_* tools. Teammates run in background
                    threads and report to the lead mailbox.
                    """);
            return;
        }

        Cli.header("s13", "Agent Teams");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        TaskStore tasks = new TaskStore(workspace.root());
        Team team = new Team(workspace, tasks, client);

        team.toolFactory((shared, member, memberWorkspace) -> {
            ToolRegistry registry = StandardTools.base(memberWorkspace, 120)
                    .register(shared.tasks().createTool())
                    .register(shared.tasks().listTool())
                    .register(shared.tasks().getTool())
                    .register(shared.tasks().claimTool())
                    .register(shared.tasks().completeTool())
                    .register(shared.sendTool(member.name()))
                    .register(shared.inboxTool(member.name()));
            return registry;
        });

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(tasks.createTool())
                .register(tasks.listTool())
                .register(tasks.getTool())
                .register(tasks.updateTool())
                .register(tasks.claimTool())
                .register(tasks.completeTool())
                .register(team.spawnTool())
                .register(team.listTool())
                .register(team.sendTool("lead"))
                .register(team.inboxTool("lead"))
                .register(team.shutdownTool());

        String system = StandardTools.systemPrompt(
                "the lead agent of a small coding team",
                workspace,
                "Plan on the shared task board. Spawn one teammate per independent task. "
                        + "Give each teammate a concrete prompt, then check team_inbox for results. "
                        + "Only one owner may hold a task at a time.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .notifications(() -> {
                    List<String> notes = new ArrayList<>();
                    for (Team.Mail mail : team.inboxMessages("lead")) {
                        notes.add("[team " + mail.from() + " / " + mail.type() + "] " + mail.content());
                    }
                    return notes;
                })
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s13", loop, history, query -> {
            if (!query.equals("/team")) {
                return false;
            }
            System.out.println(team.list());
            System.out.println();
            System.out.println(tasks.render());
            return true;
        });
    }
}
