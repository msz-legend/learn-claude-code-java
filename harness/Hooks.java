package harness;

import java.util.List;

/**
 * Extension points around the loop. Hooks can observe, rewrite tool output, or
 * block a tool call without changing AgentLoop itself.
 */
public interface Hooks {
    default void userPromptSubmit(String prompt) throws Exception {
    }

    /** Return null to allow the call, or a non-null reason to block it. */
    default String preToolUse(Message.Block.ToolUse call) throws Exception {
        return null;
    }

    /** Return the output the model should see. */
    default String postToolUse(Message.Block.ToolUse call, String output) throws Exception {
        return output;
    }

    default void stop(List<Message.Msg> history) throws Exception {
    }

    static Hooks none() {
        return new Hooks() {
        };
    }

    static Hooks compose(Hooks... hooks) {
        return new Hooks() {
            @Override
            public void userPromptSubmit(String prompt) throws Exception {
                for (Hooks hook : hooks) {
                    hook.userPromptSubmit(prompt);
                }
            }

            @Override
            public String preToolUse(Message.Block.ToolUse call) throws Exception {
                for (Hooks hook : hooks) {
                    String reason = hook.preToolUse(call);
                    if (reason != null) {
                        return reason;
                    }
                }
                return null;
            }

            @Override
            public String postToolUse(Message.Block.ToolUse call, String output) throws Exception {
                String current = output;
                for (Hooks hook : hooks) {
                    current = hook.postToolUse(call, current);
                }
                return current;
            }

            @Override
            public void stop(List<Message.Msg> history) throws Exception {
                for (Hooks hook : hooks) {
                    hook.stop(history);
                }
            }
        };
    }
}
