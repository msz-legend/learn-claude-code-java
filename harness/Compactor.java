package harness;

import java.util.List;

/** Called before every model request; s08 supplies the real implementation. */
@FunctionalInterface
public interface Compactor {
    void beforeRequest(List<Message.Msg> history, LlmClient client) throws Exception;

    static Compactor none() {
        return (history, client) -> {
        };
    }
}
