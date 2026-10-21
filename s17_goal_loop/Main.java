package s17_goal_loop;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.Goal;
import harness.LlmClient;
import harness.Message;
import harness.Permission;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s17: Goal Loop
 *
 * The worker may propose a stop by ending its turn. An independent evaluator
 * reads the transcript and either accepts the goal, rejects it with a reason,
 * or declares it impossible. The loop continues until one of those happens.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s17: Goal Loop

                    Usage:
                      java -cp out s17_goal_loop.Main --goal "make the tests pass"
                      java -cp out s17_goal_loop.Main          # prompt for a goal

                    Environment:
                      GOAL_MAX_TURNS   evaluator budget (default 12)
                      PERMISSION_MODE  ask (default) or allow
                    """);
            return;
        }

        String goalText = parseGoal(args);
        if (goalText == null || goalText.isBlank()) {
            goalText = Cli.readLine("Goal: ");
        }
        if (goalText == null || goalText.isBlank()) {
            System.out.println("No goal given. Use --goal \"...\".");
            return;
        }

        Cli.header("s17", "Goal Loop");
        System.out.println("goal: " + goalText);
        System.out.println();

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        Goal goal = new Goal(goalText, client);

        ToolRegistry tools = StandardTools.base(workspace, 120);

        Permission.Policy policy = Env.get("PERMISSION_MODE", "ask").equalsIgnoreCase("allow")
                ? Permission.allowAll()
                : Permission.rules(Permission.defaultRules());

        String system = StandardTools.systemPrompt(
                "a coding agent working against a checkable goal",
                workspace,
                "The goal is:\n" + goalText + "\n\n"
                        + "Work until the goal is met. Verify with a concrete command and quote its output. "
                        + "If you stop, an independent evaluator will inspect the transcript and may send you back.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .permission(policy)
                .approver(Cli.consoleApprover())
                .stopGate(goal)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        history.add(Message.user("Goal: " + goalText + "\nStart by inspecting the workspace."));

        String result = loop.run(history);
        System.out.println();
        System.out.println(Cli.color("status: " + loop.lastStatus(), Cli.CYAN));
        System.out.println(Cli.color(goal.statusLine(), Cli.DIM));
        if (!result.isBlank()) {
            System.out.println();
            System.out.println(result);
        }
    }

    private static String parseGoal(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--goal") && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        String env = Env.get("GOAL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        for (String arg : args) {
            if (!arg.startsWith("-")) {
                return arg;
            }
        }
        return null;
    }
}
