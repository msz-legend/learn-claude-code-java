package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Four layers of context control, cheapest first:
 *
 *   1. cap the total size of tool results (persist the full text to disk)
 *   2. truncate individual large tool results
 *   3. keep a recent window and summarize older turns
 *   4. the summary is a model call, so it only runs when the cheaper layers
 *      cannot get under the budget
 */
public final class ContextCompactor implements Compactor {
    private final Path archiveDir;
    private final int toolResultBudgetChars;
    private final int summarizeAtTokens;
    private final int keepMessages;
    private int compactions;

    public ContextCompactor(Path workspace) {
        this.archiveDir = workspace.resolve(".compact");
        this.toolResultBudgetChars = Env.getInt("COMPACT_TOOL_RESULT_BUDGET", 200_000);
        this.summarizeAtTokens = Env.getInt("COMPACT_SUMMARIZE_AT_TOKENS", 24_000);
        this.keepMessages = Env.getInt("COMPACT_KEEP_MESSAGES", 8);
    }

    @Override
    public void beforeRequest(List<Message.Msg> history, LlmClient client) throws Exception {
        if (history.isEmpty()) {
            return;
        }
        budgetToolResults(history);
        truncateLargeToolResults(history, 4_000, 2_000);
        if (estimateTokens(history) > summarizeAtTokens) {
            summarize(history, client);
        }
        if (estimateTokens(history) > summarizeAtTokens * 2) {
            truncateLargeToolResults(history, 1_200, 600);
        }
    }

    public int compactions() {
        return compactions;
    }

    public static int estimateTokens(List<Message.Msg> history) {
        int chars = 0;
        for (Message.Msg message : history) {
            chars += message.role().length();
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.Text text) {
                    chars += text.text().length();
                } else if (block instanceof Message.Block.ToolUse use) {
                    chars += use.name().length() + Json.stringify(use.input()).length();
                } else if (block instanceof Message.Block.ToolResult result) {
                    chars += result.content().length();
                }
            }
        }
        return Math.max(1, chars / 4);
    }

    public static String transcript(List<Message.Msg> history, int maxChars) {
        StringBuilder out = new StringBuilder();
        for (Message.Msg message : history) {
            if (out.length() >= maxChars) {
                break;
            }
            out.append('[').append(message.role()).append("]\n");
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.Text text) {
                    out.append(text.text()).append('\n');
                } else if (block instanceof Message.Block.ToolUse use) {
                    out.append("tool_use ").append(use.name()).append(' ')
                            .append(Json.stringify(use.input())).append('\n');
                } else if (block instanceof Message.Block.ToolResult result) {
                    out.append("tool_result ").append(result.toolUseId()).append(": ")
                            .append(result.content()).append('\n');
                }
            }
            out.append('\n');
        }
        if (out.length() > maxChars) {
            out.setLength(maxChars);
            out.append("\n... (transcript truncated)");
        }
        return out.toString();
    }

    private void budgetToolResults(List<Message.Msg> history) {
        int total = 0;
        for (Message.Msg message : history) {
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.ToolResult result) {
                    total += result.content().length();
                }
            }
        }
        if (total <= toolResultBudgetChars) {
            return;
        }

        for (int i = 0; i < history.size() && total > toolResultBudgetChars; i++) {
            Message.Msg message = history.get(i);
            List<Message.Block> blocks = new ArrayList<>();
            boolean changed = false;
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.ToolResult result
                        && result.content().length() > 2_000
                        && !result.content().startsWith("[archived]")) {
                    String path = archive(result.toolUseId(), result.content());
                    String preview = headTail(result.content(), 1_500, 500)
                            + "\n[archived] full output: " + path;
                    blocks.add(new Message.Block.ToolResult(result.toolUseId(), preview, result.error()));
                    total -= result.content().length() - preview.length();
                    changed = true;
                } else {
                    blocks.add(block);
                }
            }
            if (changed) {
                history.set(i, new Message.Msg(message.role(), blocks));
            }
        }
    }

    private void truncateLargeToolResults(List<Message.Msg> history, int threshold, int keep) {
        for (int i = 0; i < history.size(); i++) {
            Message.Msg message = history.get(i);
            List<Message.Block> blocks = new ArrayList<>();
            boolean changed = false;
            for (Message.Block block : message.blocks()) {
                if (block instanceof Message.Block.ToolResult result
                        && result.content().length() > threshold
                        && !result.content().startsWith("[archived]")) {
                    String preview = headTail(result.content(), keep, keep / 2)
                            + "\n... (older tool output shortened)";
                    blocks.add(new Message.Block.ToolResult(result.toolUseId(), preview, result.error()));
                    changed = true;
                } else {
                    blocks.add(block);
                }
            }
            if (changed) {
                history.set(i, new Message.Msg(message.role(), blocks));
            }
        }
    }

    private void summarize(List<Message.Msg> history, LlmClient client) throws Exception {
        int keep = Math.min(keepMessages, history.size());
        int cut = history.size() - keep;
        // Never cut between an assistant tool_use and its tool_result. Move the
        // boundary forward to the next plain user message.
        while (cut < history.size() && !isPlainUserMessage(history.get(cut))) {
            cut++;
        }
        if (cut <= 0 || cut >= history.size()) {
            return;
        }

        List<Message.Msg> older = new ArrayList<>(history.subList(0, cut));
        String transcript = transcript(older, 80_000);
        String prompt = """
                Summarize the conversation below so another turn can continue the work.
                Keep: the user's goal, decisions, files and commands that mattered, concrete
                results, and unfinished tasks. Drop small talk and redundant tool output.
                Write a compact handoff note, not an essay.

                Conversation:
                """ + transcript;

        Message.LlmResponse response = client.complete(
                "You compress conversation history for a coding agent. Be accurate and concise.",
                List.of(Message.user(prompt)),
                List.of());
        String summary = response.text().strip();
        if (summary.isEmpty()) {
            return;
        }

        List<Message.Msg> next = new ArrayList<>();
        next.add(Message.assistant(List.of(new Message.Block.Text(
                "[Earlier conversation summary]\n" + summary))));
        next.addAll(history.subList(cut, history.size()));
        history.clear();
        history.addAll(next);
        compactions++;
        System.out.println("[compact] summarized " + older.size()
                + " messages; kept " + (next.size() - 1));
    }

    private static boolean isPlainUserMessage(Message.Msg message) {
        if (!"user".equals(message.role())) {
            return false;
        }
        for (Message.Block block : message.blocks()) {
            if (block instanceof Message.Block.ToolResult) {
                return false;
            }
        }
        return true;
    }

    private String archive(String toolUseId, String content) {
        try {
            Files.createDirectories(archiveDir);
            String safe = toolUseId == null || toolUseId.isBlank()
                    ? UUID.randomUUID().toString()
                    : toolUseId.replaceAll("[^a-zA-Z0-9_.-]", "_");
            Path file = archiveDir.resolve(safe + ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file.toString();
        } catch (IOException e) {
            return "(archive failed: " + e.getMessage() + ")";
        }
    }

    private static String headTail(String text, int head, int tail) {
        if (text.length() <= head + tail + 40) {
            return text;
        }
        return text.substring(0, head)
                + "\n... (" + (text.length() - head - tail) + " chars omitted) ...\n"
                + text.substring(text.length() - tail);
    }
}
