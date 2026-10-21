package harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Chat Completions API.
 *
 * Works with OpenAI, DeepSeek, GLM/Zhipu, Qwen/DashScope compatible mode,
 * Moonshot, Ollama and other providers that implement the same shape.
 */
public final class OpenAiClient implements LlmClient {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public OpenAiClient() {
        this.apiKey = Env.require("OPENAI_API_KEY");
        this.baseUrl = Env.baseUrl();
        this.model = Env.model();
        this.timeoutSeconds = Env.timeoutSeconds();
    }

    @Override
    public Message.LlmResponse complete(
            String system,
            List<Message.Msg> messages,
            List<Message.ToolSpec> tools) throws Exception {
        List<Object> encoded = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            encoded.add(Json.obj("role", "system", "content", system));
        }
        for (Message.Msg message : messages) {
            encodeMessage(message, encoded);
        }

        Map<String, Object> body = Json.obj(
                "model", model,
                "max_tokens", Env.maxTokens(),
                "messages", encoded);
        if (!tools.isEmpty()) {
            body.put("tools", encodeTools(tools));
        }

        String raw = Http.postJson(
                baseUrl + "/chat/completions",
                Map.of("authorization", "Bearer " + apiKey),
                Json.stringify(body),
                timeoutSeconds);

        Map<String, Object> response = Json.object(Json.parse(raw));
        List<Object> choices = Json.array(response.get("choices"));
        if (choices.isEmpty()) {
            throw new IllegalStateException("Provider returned no choices: " + shorten(raw));
        }
        Map<String, Object> choice = Json.object(choices.get(0));
        Map<String, Object> message = Json.object(choice.get("message"));

        List<Message.Block> blocks = new ArrayList<>();
        String text = contentText(message.get("content"));
        if (!text.isBlank()) {
            blocks.add(new Message.Block.Text(text));
        }

        Object rawCalls = message.get("tool_calls");
        if (rawCalls instanceof List<?> calls) {
            int index = 0;
            for (Object item : calls) {
                Map<String, Object> call = Json.object(item);
                Map<String, Object> function = call.get("function") instanceof Map<?, ?>
                        ? Json.object(call.get("function"))
                        : Map.of();
                String name = Json.str(function, "name", "");
                if (name.isBlank()) {
                    continue;
                }
                String id = Json.str(call, "id", "call_" + index++);
                blocks.add(new Message.Block.ToolUse(id, name, decodeArguments(function.get("arguments"))));
            }
        }

        return new Message.LlmResponse(blocks, Json.str(choice, "finish_reason", ""));
    }

    @Override
    public String model() {
        return model;
    }

    private static void encodeMessage(Message.Msg message, List<Object> out) {
        if ("assistant".equals(message.role())) {
            String text = message.text();
            List<Object> calls = new ArrayList<>();
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.ToolUse use) {
                    calls.add(Json.obj(
                            "id", use.id(),
                            "type", "function",
                            "function", Json.obj(
                                    "name", use.name(),
                                    "arguments", Json.stringify(use.input()))));
                }
            }
            Map<String, Object> encoded = Json.obj("role", "assistant");
            encoded.put("content", text.isEmpty() ? null : text);
            if (!calls.isEmpty()) {
                encoded.put("tool_calls", calls);
            }
            out.add(encoded);
            return;
        }

        for (Message.Block block : message.blocks()) {
            if (block instanceof Message.Block.Text text) {
                out.add(Json.obj("role", "user", "content", text.text()));
            } else if (block instanceof Message.Block.ToolResult result) {
                out.add(Json.obj(
                        "role", "tool",
                        "tool_call_id", result.toolUseId(),
                        "content", result.content()));
            }
        }
    }

    private static List<Object> encodeTools(List<Message.ToolSpec> tools) {
        List<Object> out = new ArrayList<>();
        for (Message.ToolSpec tool : tools) {
            out.add(Json.obj(
                    "type", "function",
                    "function", Json.obj(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", tool.inputSchema())));
        }
        return out;
    }

    private static String contentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> parts) {
            StringBuilder out = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?>) {
                    Map<String, Object> map = Json.object(part);
                    String text = Json.str(map, "text", "");
                    if (!text.isEmpty()) {
                        if (!out.isEmpty()) {
                            out.append('\n');
                        }
                        out.append(text);
                    }
                }
            }
            return out.toString();
        }
        return String.valueOf(content);
    }

    private static Map<String, Object> decodeArguments(Object value) {
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

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ').trim();
        return oneLine.length() > 500 ? oneLine.substring(0, 500) + "..." : oneLine;
    }
}
