package harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Rule-based allow / deny / ask decisions before a tool runs. */
public final class Permission {
    private Permission() {
    }

    public enum Mode {
        ALLOW, DENY, ASK
    }

    public record Decision(Mode mode, String reason) {
        public static Decision allow() {
            return new Decision(Mode.ALLOW, "");
        }

        public static Decision deny(String reason) {
            return new Decision(Mode.DENY, reason);
        }

        public static Decision ask(String reason) {
            return new Decision(Mode.ASK, reason);
        }
    }

    public interface Policy {
        Decision check(Message.Block.ToolUse call, Tool tool);
    }

    public interface Approver {
        boolean approve(Message.Block.ToolUse call, String reason);
    }

    public record Rule(String tool, String contains, Mode mode, String reason) {
        public boolean matches(Message.Block.ToolUse call) {
            if (!"*".equals(tool) && !tool.equals(call.name())) {
                return false;
            }
            if (contains == null || contains.isBlank()) {
                return true;
            }
            String haystack = searchable(call.input());
            return haystack.contains(contains.toLowerCase(Locale.ROOT));
        }
    }

    public static Policy allowAll() {
        return (call, tool) -> Decision.allow();
    }

    public static Policy rules(List<Rule> rules) {
        List<Rule> copy = new ArrayList<>(rules);
        return (call, tool) -> {
            for (Rule rule : copy) {
                if (rule.matches(call)) {
                    return new Decision(rule.mode(), rule.reason());
                }
            }
            return Decision.allow();
        };
    }

    public static List<Rule> defaultRules() {
        List<Rule> rules = new ArrayList<>();
        for (String dangerous : List.of("rm -rf /", "sudo ", "shutdown", "reboot", "mkfs", "dd if=", "> /dev/", ":(){:|:&};:")) {
            rules.add(new Rule("bash", dangerous, Mode.DENY, "Dangerous command blocked: " + dangerous));
        }
        rules.add(new Rule("bash", "rm ", Mode.ASK, "This command deletes files"));
        rules.add(new Rule("bash", "git reset --hard", Mode.ASK, "This command discards local changes"));
        rules.add(new Rule("bash", "git push --force", Mode.ASK, "This command rewrites remote history"));
        rules.add(new Rule("bash", "git clean -fd", Mode.ASK, "This command deletes untracked files"));
        rules.add(new Rule("bash", "chmod 777", Mode.ASK, "This command widens file permissions"));
        rules.add(new Rule("write_file", ".env", Mode.ASK, "This writes a secrets file"));
        return rules;
    }

    public static boolean isDangerous(String command) {
        if (command == null) {
            return false;
        }
        String text = command.toLowerCase(Locale.ROOT);
        for (String marker : List.of("rm -rf /", "sudo ", "shutdown", "reboot", "mkfs", "dd if=", "> /dev/", ":(){:|:&};:")) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String searchable(Map<String, Object> input) {
        StringBuilder out = new StringBuilder();
        for (Object value : input.values()) {
            if (value != null) {
                out.append(value).append('\n');
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
