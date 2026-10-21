package harness;

import java.util.LinkedHashMap;
import java.util.Map;

/** One callable capability exposed to the model. */
public record Tool(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Handler handler) {

    public Tool {
        inputSchema = inputSchema == null ? Map.of() : new LinkedHashMap<>(inputSchema);
    }

    @FunctionalInterface
    public interface Handler {
        String execute(Map<String, Object> input) throws Exception;
    }

    public record Result(String output, boolean error) {
    }

    public Message.ToolSpec spec() {
        return new Message.ToolSpec(name, description, inputSchema);
    }

    public Result run(Map<String, Object> input) {
        try {
            String output = handler.execute(input == null ? Map.of() : input);
            if (output == null) {
                output = "(no output)";
            }
            return new Result(output, output.startsWith("Error:"));
        } catch (Exception e) {
            String message = e.getMessage();
            return new Result("Error: " + (message == null ? e.getClass().getSimpleName() : message), true);
        }
    }
}
