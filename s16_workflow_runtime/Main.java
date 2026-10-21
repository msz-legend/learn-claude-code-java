package s16_workflow_runtime;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workflow;
import harness.Workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * s16: Workflow Runtime
 *
 * The host owns the orchestration shape; the model only chooses a workflow
 * name. Agent calls and steps are journaled under .workflows/<run>/ so a
 * restart can resume without repeating completed work.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s16: Workflow Runtime

                    Usage:
                      java -cp out s16_workflow_runtime.Main [run-id]

                    The default "review" workflow inspects the workspace, runs two
                    reviews in parallel, and writes a report. Pass a run id to resume
                    a journaled run. The model may also call the workflow tool.
                    """);
            return;
        }

        Cli.header("s16", "Workflow Runtime");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();

        ToolRegistry reviewTools = StandardTools.base(workspace, 120);
        String reviewSystem = StandardTools.systemPrompt(
                "a focused reviewer",
                workspace,
                "Read the actual files. Report concrete findings, not generic advice.");

        Workflow.AgentCall agentCall = prompt -> {
            List<Message.Msg> history = new ArrayList<>();
            history.add(Message.user(prompt));
            return new AgentLoop(client, reviewTools, reviewSystem)
                    .maxTurns(Env.getInt("WORKFLOW_AGENT_TURNS", 10))
                    .listener(Cli.printingListener())
                    .run(history);
        };

        Map<String, String> state = new LinkedHashMap<>();

        List<Workflow.Step> steps = List.of(
                new Workflow.Step("inspect", ctx -> {
                    ctx.phase("Inspect");
                    String output = ctx.agent("""
                            Inspect this repository. Summarize its purpose, layout, build
                            command, and the three most important files. Keep it under 400 words.
                            """);
                    state.put("inspect", output);
                    return output;
                }),
                new Workflow.Step("review", ctx -> {
                    ctx.phase("Review");
                    List<String> outputs = ctx.parallel(List.of(
                            "Review the code for correctness bugs. Read the actual files. "
                                    + "List findings with file names and severity.",
                            "Review the code for missing tests and unclear documentation. "
                                    + "Read the actual files. List concrete gaps."));
                    StringBuilder joined = new StringBuilder();
                    for (int i = 0; i < outputs.size(); i++) {
                        joined.append("Review ").append(i + 1).append(":\n")
                                .append(outputs.get(i)).append("\n\n");
                    }
                    state.put("review", joined.toString().strip());
                    return state.get("review");
                }),
                new Workflow.Step("report", ctx -> {
                    ctx.phase("Report");
                    String report = ctx.agent("""
                            Write a short review report for the repository.
                            Use only these findings; do not invent new ones.

                            Inspection:
                            %s

                            Reviews:
                            %s
                            """.formatted(state.get("inspect"), state.get("review")));
                    state.put("report", report);
                    return report;
                }));

        Workflow.Registry registry = new Workflow.Registry(workspace.root(), agentCall)
                .register(new Workflow.Meta("review",
                        "Inspect, review in parallel, then write a report.", List.of("inspect", "review", "report")),
                        steps)
                .register(new Workflow.Meta("summarize",
                        "One agent call that summarizes the repository.", List.of("summarize")),
                        List.of(new Workflow.Step("summarize", ctx -> ctx.agent(
                                "Summarize this repository for a new contributor in under 300 words."))));

        ToolRegistry tools = StandardTools.base(workspace, 120).register(registry.tool());
        String system = StandardTools.systemPrompt(
                "a coding agent that can run saved workflows",
                workspace,
                "For a multi-phase review, call workflow with name=review. "
                        + "The workflow runtime owns the steps and journal.\n\n"
                        + registry.renderCatalog());

        if (args.length > 0) {
            Workflow.Result result = registry.run("review", args[0]);
            System.out.println(result.render());
            System.out.println();
            System.out.println(state.getOrDefault("report", "(no report)"));
            return;
        }

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s16", loop, history, query -> {
            if (!query.startsWith("/run ")) {
                return false;
            }
            String[] parts = query.substring("/run ".length()).strip().split("\\s+");
            String name = parts.length > 0 ? parts[0] : "review";
            String runId = parts.length > 1 ? parts[1] : "";
            try {
                Workflow.Result result = registry.run(name, runId);
                System.out.println(result.render());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            return true;
        });
    }
}
