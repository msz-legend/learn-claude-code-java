package s03_permission;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.Permission;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * s03: Permission System
 *
 * Three gates before a tool runs:
 *   1. hard deny rules (rm -rf /, sudo, ...)
 *   2. rule matching (deletes, force pushes, secrets)
 *   3. an interactive approval for ASK rules
 *
 * A blocked call still produces a tool_result, so the model can recover.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s03: Permission System

                    Usage:
                      java -cp out s03_permission.Main

                    Destructive commands are denied or require interactive approval.
                    Set PERMISSION_MODE=allow to disable the gate for a quick demo.
                    """);
            return;
        }

        Cli.header("s03", "Permission System");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        ToolRegistry tools = StandardTools.base(workspace, 120);

        Permission.Policy policy = Env.get("PERMISSION_MODE", "ask").equalsIgnoreCase("allow")
                ? Permission.allowAll()
                : Permission.rules(Permission.defaultRules());

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Destructive operations require user approval. "
                        + "If a tool is denied, explain what you need and choose a safer action.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .permission(policy)
                .approver(Cli.consoleApprover())
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s03", loop, history, query -> {
            if (!query.equals("/rules")) {
                return false;
            }
            for (Permission.Rule rule : Permission.defaultRules()) {
                System.out.println("- " + rule.mode() + " " + rule.tool()
                        + (rule.contains() == null || rule.contains().isBlank() ? "" : " contains=" + rule.contains())
                        + "  (" + rule.reason() + ")");
            }
            return true;
        });
    }
}
