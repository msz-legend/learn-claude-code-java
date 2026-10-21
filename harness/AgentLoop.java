package harness;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one loop the whole course is built around:
 *
 *   call the model -> execute requested tools -> append results -> repeat
 *
 * Permissions, hooks, compaction, notifications and the stop gate are optional
 * ports around the same loop. Later chapters wire them; the loop itself stays.
 */
public final class AgentLoop {
    private final LlmClient client;
    private final ToolRegistry tools;
    private String system;
    private Permission.Policy permission = Permission.allowAll();
    private Permission.Approver approver;
    private Hooks hooks = Hooks.none();
    private Compactor compactor = Compactor.none();
    private Supplier<List<String>> notifications;
    private StopGate stopGate;
    private Listener listener = new Listener() {
    };
    private int maxTurns;
    private String lastStatus = "completed";

    public AgentLoop(LlmClient client, ToolRegistry tools, String system) {
        this.client = client;
        this.tools = tools;
        this.system = system;
    }

    public AgentLoop permission(Permission.Policy policy) {
        this.permission = policy == null ? Permission.allowAll() : policy;
        return this;
    }

    public AgentLoop approver(Permission.Approver value) {
        this.approver = value;
        return this;
    }

    public AgentLoop hooks(Hooks value) {
        this.hooks = value == null ? Hooks.none() : value;
        return this;
    }

    public AgentLoop compactor(Compactor value) {
        this.compactor = value == null ? Compactor.none() : value;
        return this;
    }

    public AgentLoop notifications(Supplier<List<String>> value) {
        this.notifications = value;
        return this;
    }

    public AgentLoop stopGate(StopGate value) {
        this.stopGate = value;
        return this;
    }

    public AgentLoop listener(Listener value) {
        this.listener = value == null ? new Listener() {
        } : value;
        return this;
    }

    public AgentLoop maxTurns(int value) {
        this.maxTurns = Math.max(0, value);
        return this;
    }

    public AgentLoop system(String value) {
        this.system = value;
        return this;
    }

    public LlmClient client() {
        return client;
    }

    public ToolRegistry tools() {
        return tools;
    }

    public String lastStatus() {
        return lastStatus;
    }

    public String run(List<Message.Msg> history) throws Exception {
        lastStatus = "working";
        for (int i = history.size() - 1; i >= 0; i--) {
            Message.Msg message = history.get(i);
            if ("user".equals(message.role())) {
                String text = message.text();
                if (!text.isBlank()) {
                    hooks.userPromptSubmit(text);
                }
                break;
            }
        }
        int turn = 0;
        while (true) {
            turn++;
            if (maxTurns > 0 && turn > maxTurns) {
                lastStatus = "max_turns";
                String message = "Stopped: the maximum turn count (" + maxTurns + ") was reached.";
                listener.onStop(message, lastStatus);
                return message;
            }
            listener.onTurnStart(turn);

            compactor.beforeRequest(history, client);

            if (notifications != null) {
                List<String> notes = notifications.get();
                if (notes != null && !notes.isEmpty()) {
                    history.add(Message.user(String.join("\n\n", notes)));
                }
            }

            Message.LlmResponse response = client.complete(system, history, tools.specs());
            history.add(Message.assistant(response.blocks()));

            String text = response.text();
            if (!text.isBlank()) {
                listener.onAssistantText(text);
            }

            List<Message.Block.ToolUse> calls = response.toolUses();
            if (calls.isEmpty()) {
                String status = "completed";
                if (stopGate != null) {
                    StopGate.Decision decision = stopGate.beforeStop(history, text);
                    if (decision != null && !decision.stop()) {
                        String reason = decision.reason() == null ? "Continue working." : decision.reason();
                        history.add(Message.user(reason));
                        continue;
                    }
                    if (decision != null && decision.status() != null) {
                        status = decision.status();
                    }
                }
                lastStatus = status;
                hooks.stop(history);
                listener.onStop(text, status);
                return text;
            }

            List<Message.Block.ToolResult> results = new ArrayList<>();
            for (Message.Block.ToolUse call : calls) {
                listener.onToolStart(call);
                Tool tool = tools.get(call.name());
                String blocked = null;

                if (tool == null) {
                    blocked = "Unknown tool: " + call.name();
                } else {
                    Permission.Decision decision = permission.check(call, tool);
                    if (decision.mode() == Permission.Mode.DENY) {
                        blocked = decision.reason().isBlank() ? "Denied by policy" : decision.reason();
                    } else if (decision.mode() == Permission.Mode.ASK) {
                        boolean approved = approver != null
                                && approver.approve(call, decision.reason());
                        if (!approved) {
                            blocked = "Permission denied: " + decision.reason();
                        }
                    }
                }

                if (blocked == null) {
                    blocked = hooks.preToolUse(call);
                }

                if (blocked != null) {
                    String output = "Error: " + blocked;
                    results.add(new Message.Block.ToolResult(call.id(), output, true));
                    listener.onToolBlocked(call, blocked);
                    continue;
                }

                Tool.Result result = tools.execute(call);
                String output = result.output();
                output = hooks.postToolUse(call, output);
                results.add(new Message.Block.ToolResult(call.id(), output, result.error()));
                listener.onToolResult(call, output, result.error());
            }

            history.add(Message.toolResults(results));
        }
    }

    public interface Listener {
        default void onTurnStart(int turn) {
        }

        default void onAssistantText(String text) {
        }

        default void onToolStart(Message.Block.ToolUse call) {
        }

        default void onToolResult(Message.Block.ToolUse call, String output, boolean error) {
        }

        default void onToolBlocked(Message.Block.ToolUse call, String reason) {
        }

        default void onStop(String text, String status) {
        }
    }
}
