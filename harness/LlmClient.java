package harness;

import java.util.List;

/** Both API shapes return the same provider-neutral response. */
public interface LlmClient {
    Message.LlmResponse complete(String system, List<Message.Msg> messages, List<Message.ToolSpec> tools)
            throws Exception;

    String model();

    static LlmClient fromEnv() {
        return switch (Env.protocol()) {
            case "anthropic" -> new AnthropicClient();
            case "openai" -> new OpenAiClient();
            default -> throw new IllegalStateException(
                    "Unknown LLM_PROTOCOL: " + Env.protocol() + " (use anthropic or openai)");
        };
    }
}
