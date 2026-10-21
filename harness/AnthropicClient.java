package harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API, also used by Anthropic-compatible providers. */
public final class AnthropicClient implements LlmClient {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public AnthropicClient() {
        this.apiKey = Env.require("ANTHROPIC_API_KEY");
        this.baseUrl = Env.baseUrl();
        this.model = Env.model();
        this.timeoutSeconds = Env.timeoutSeconds();
    }

    @Override
    public Message.LlmResponse complete(
            String system,
            List<Message.Msg> messages,
            List<Message.ToolSpec> tools) throws Exception {
        Map<String, Object> body = Json.obj(
                "model", model,
                "max_tokens", Env.maxTokens(),
                "messages", encodeMessages(messages));
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        if (!tools.isEmpty()) {
            body.put("tools", encodeTools(tools));
        }

        String raw = Http.postJson(
                baseUrl + "/v1/messages",
                Map.of(
                        "x-api-key", apiKey,
                        "anthropic-version", "2023-06-01"),
                Json.stringify(body),
                timeoutSeconds);

        Map<String, Object> response = Json.object(Json.parse(raw));
        List<Message.Block> blocks = new ArrayList<>();
        Object content = response.get("content");
        if (content instanceof String text) {
            blocks.add(new Message.Block.Text(text));
        } else if (content instanceof List<?> items) {
            for (Object item : items) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> block = Json.object(item);
                String type = Json.str(block, "type", "");
                switch (type) {
                    case "text" -> blocks.add(new Message.Block.Text(Json.str(block, "text", "")));
                    case "tool_use" -> blocks.add(new Message.Block.ToolUse(
                            Json.str(block, "id", "tool-" + blocks.size()),
                            Json.str(block, "name", ""),
                            decodeInput(block.get("input"))));
                    default -> {
                        // Thinking/reasoning blocks are not part of the teaching model.
                    }
                }
            }
        }
        return new Message.LlmResponse(blocks, Json.str(response, "stop_reason", ""));
    }

    @Override
    public String model() {
        return model;
    }

    private static List<Object> encodeMessages(List<Message.Msg> messages) {
        List<Object> out = new ArrayList<>();
        for (Message.Msg message : messages) {
            List<Object> content = new ArrayList<>();
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.Text text) {
                    content.add(Json.obj("type", "text", "text", text.text()));
                } else if (block instanceof Message.Block.ToolUse use) {
                    content.add(Json.obj(
                            "type", "tool_use",
                            "id", use.id(),
                            "name", use.name(),
                            "input", use.input()));
                } else if (block instanceof Message.Block.ToolResult result) {
                    content.add(Json.obj(
                            "type", "tool_result",
                            "tool_use_id", result.toolUseId(),
                            "content", result.content(),
                            "is_error", result.error()));
                }
            }
            out.add(Json.obj("role", message.role(), "content", content));
        }
        return out;
    }

    private static List<Object> encodeTools(List<Message.ToolSpec> tools) {
        List<Object> out = new ArrayList<>();
        for (Message.ToolSpec tool : tools) {
            out.add(Json.obj(
                    "name", tool.name(),
                    "description", tool.description(),
                    "input_schema", tool.inputSchema()));
        }
        return out;
    }

    private static Map<String, Object> decodeInput(Object value) {
        if (value instanceof Map<?, ?>) {
            return Json.object(value);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Json.object(Json.parse(text));
            } catch (IllegalArgumentException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }
}
