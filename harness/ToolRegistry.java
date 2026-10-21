package harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The dispatch table. Adding a tool never changes the agent loop. */
public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Map<String, Tool> all() {
        return Map.copyOf(tools);
    }

    public List<Message.ToolSpec> specs() {
        List<Message.ToolSpec> specs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            specs.add(tool.spec());
        }
        return specs;
    }

    public Tool.Result execute(Message.Block.ToolUse call) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return new Tool.Result("Error: unknown tool: " + call.name(), true);
        }
        return tool.run(call.input());
    }
}
