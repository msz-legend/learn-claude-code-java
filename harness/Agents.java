package harness;

import java.util.ArrayList;
import java.util.List;

/** Helpers for spawning a subagent with a fresh message list. */
public final class Agents {
    private Agents() {
    }

    public static Tool subagentTool(
            LlmClient client,
            ToolRegistry tools,
            String system,
            int maxTurns) {
        return new Tool(
                "task",
                "Run a focused subtask in a fresh context. Returns only the final text.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "description", Json.obj("type", "string"),
                                "prompt", Json.obj("type", "string")),
                        "required", Json.list("description")),
                input -> {
                    String prompt = Json.str(input, "prompt", Json.str(input, "description", ""));
                    if (prompt.isBlank()) {
                        return "Error: description is required";
                    }
                    System.out.println("[subagent] " + Cli.shorten(prompt, 160));
                    List<Message.Msg> history = new ArrayList<>();
                    history.add(Message.user(prompt));
                    AgentLoop loop = new AgentLoop(client, tools, system)
                            .maxTurns(maxTurns)
                            .listener(new AgentLoop.Listener() {
                                @Override
                                public void onToolStart(Message.Block.ToolUse call) {
                                    System.out.println("  [subagent] > " + call.name());
                                }
                            });
                    String text = loop.run(history);
                    return text.isBlank() ? "(subagent returned no text)" : text;
                });
    }
}
