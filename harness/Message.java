package harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Provider-neutral conversation model. */
public final class Message {
    private Message() {
    }

    public record Msg(String role, List<Block> blocks) {
        public Msg {
            blocks = List.copyOf(blocks);
        }

        public String text() {
            return Block.textOf(blocks);
        }
    }

    public sealed interface Block permits Block.Text, Block.ToolUse, Block.ToolResult {
        record Text(String text) implements Block {
        }

        record ToolUse(String id, String name, Map<String, Object> input) implements Block {
            public ToolUse {
                input = input == null ? Map.of() : new LinkedHashMap<>(input);
            }
        }

        record ToolResult(String toolUseId, String content, boolean error) implements Block {
        }

        static String textOf(List<Block> blocks) {
            StringBuilder out = new StringBuilder();
            for (Block block : blocks) {
                if (block instanceof Text text) {
                    if (!out.isEmpty()) {
                        out.append('\n');
                    }
                    out.append(text.text());
                }
            }
            return out.toString();
        }

        static List<ToolUse> toolUses(List<Block> blocks) {
            List<ToolUse> calls = new ArrayList<>();
            for (Block block : blocks) {
                if (block instanceof ToolUse use) {
                    calls.add(use);
                }
            }
            return calls;
        }
    }

    public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
    }

    public record LlmResponse(List<Block> blocks, String stopReason) {
        public LlmResponse {
            blocks = List.copyOf(blocks);
        }

        public String text() {
            return Block.textOf(blocks);
        }

        public List<Block.ToolUse> toolUses() {
            return Block.toolUses(blocks);
        }
    }

    public static Msg user(String text) {
        return new Msg("user", List.of(new Block.Text(text)));
    }

    public static Msg assistant(List<Block> blocks) {
        return new Msg("assistant", blocks);
    }

    public static Msg toolResults(List<Block.ToolResult> results) {
        return new Msg("user", new ArrayList<>(results));
    }
}
