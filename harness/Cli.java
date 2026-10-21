package harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/** Shared console helpers so chapter entry points stay short. */
public final class Cli {
    public static final String RESET = "\u001b[0m";
    public static final String CYAN = "\u001b[36m";
    public static final String YELLOW = "\u001b[33m";
    public static final String RED = "\u001b[31m";
    public static final String DIM = "\u001b[2m";

    private static final boolean COLOR = System.getenv("NO_COLOR") == null;
    private static final BufferedReader IN = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private Cli() {
    }

    public static String color(String text, String code) {
        return COLOR ? code + text + RESET : text;
    }

    public static void header(String chapter, String title) {
        System.out.println(color(chapter + ": " + title, CYAN));
        System.out.println(color("model: " + Env.describeModel(), DIM));
        System.out.println(color("workspace: " + Env.workdir(), DIM));
        System.out.println();
    }

    public static void repl(
            String prompt,
            AgentLoop loop,
            List<Message.Msg> history,
            Function<String, Boolean> commandHandler) {
        while (true) {
            String line = readLine(color(prompt + " >> ", CYAN));
            if (line == null) {
                break;
            }
            String query = line.strip();
            if (query.isEmpty() || query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit")) {
                break;
            }
            if (query.startsWith("/") && commandHandler != null && commandHandler.apply(query)) {
                continue;
            }
            history.add(Message.user(query));
            try {
                String text = loop.run(history);
                if (!text.isBlank()) {
                    System.out.println(text);
                }
                System.out.println();
            } catch (Exception e) {
                System.out.println(color("Error: " + e.getMessage(), RED));
            }
        }
        System.out.println("bye.");
    }

    public static String readLine(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            return IN.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    public static Permission.Approver consoleApprover() {
        return (call, reason) -> {
            System.out.println();
            System.out.println(color("permission requested: " + reason, YELLOW));
            System.out.println("  " + call.name() + " " + shorten(Json.stringify(call.input()), 400));
            String answer = readLine("  allow? [y/N] ");
            return answer != null && answer.strip().toLowerCase().startsWith("y");
        };
    }

    public static AgentLoop.Listener printingListener() {
        return new AgentLoop.Listener() {
            @Override
            public void onToolStart(Message.Block.ToolUse call) {
                System.out.println(color("> " + call.name() + " " + shorten(Json.stringify(call.input()), 240), YELLOW));
            }

            @Override
            public void onToolResult(Message.Block.ToolUse call, String output, boolean error) {
                String prefix = error ? "  ! " : "  ";
                System.out.println(color(prefix + shorten(output, 600).replace("\n", "\n  "), DIM));
            }

            @Override
            public void onToolBlocked(Message.Block.ToolUse call, String reason) {
                System.out.println(color("  x " + reason, RED));
            }
        };
    }

    public static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\r', ' ').strip();
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max) + "...";
    }
}
