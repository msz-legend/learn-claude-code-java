package harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Environment and .env loading shared by every chapter. */
public final class Env {
    private static final Map<String, String> DOTENV = new LinkedHashMap<>();
    private static final Path WORKDIR = Paths.get("").toAbsolutePath().normalize();

    static {
        loadDotEnv();
    }

    private Env() {
    }

    public static Path workdir() {
        return WORKDIR;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String environmentPrompt() {
        return isWindows()
                ? "Windows: the bash tool runs through cmd.exe; use cmd.exe syntax, not Unix Bash "
                        + "or PowerShell syntax, and prefer dedicated file tools for file operations"
                : "Unix-like: the bash tool runs the system shell";
    }

    public static String get(String key) {
        String value = DOTENV.get(key);
        if (value != null) {
            return value;
        }
        return System.getenv(key);
    }

    public static String get(String key, String fallback) {
        String value = get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing " + key + ". Copy .env.example to .env and fill in your model settings.");
        }
        return value;
    }

    public static int getInt(String key, int fallback) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean getBool(String key, boolean fallback) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> fallback;
        };
    }

    /**
     * Protocol selection. An explicit LLM_PROTOCOL wins. Otherwise the presence
     * of an API key decides, so the common single-provider setup needs no extra
     * configuration.
     */
    public static String protocol() {
        String explicit = get("LLM_PROTOCOL");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase(Locale.ROOT);
        }
        if (get("ANTHROPIC_API_KEY") != null && !get("ANTHROPIC_API_KEY").isBlank()) {
            return "anthropic";
        }
        if (get("OPENAI_API_KEY") != null && !get("OPENAI_API_KEY").isBlank()) {
            return "openai";
        }
        return "anthropic";
    }

    public static String model() {
        String model = get("MODEL_ID");
        if (model != null && !model.isBlank()) {
            return model;
        }
        return "openai".equals(protocol())
                ? get("OPENAI_MODEL", "gpt-4o-mini")
                : get("ANTHROPIC_MODEL", "claude-sonnet-4-5");
    }

    public static String baseUrl() {
        if ("openai".equals(protocol())) {
            return stripTrailingSlash(get("OPENAI_BASE_URL", "https://api.openai.com/v1"));
        }
        return stripTrailingSlash(get("ANTHROPIC_BASE_URL", "https://api.anthropic.com"));
    }

    public static int maxTokens() {
        return getInt("MAX_TOKENS", 8000);
    }

    public static int timeoutSeconds() {
        return getInt("LLM_TIMEOUT_SECONDS", 180);
    }

    public static String describeModel() {
        return protocol() + " / " + model() + " @ " + baseUrl();
    }

    private static String stripTrailingSlash(String value) {
        String out = value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static void loadDotEnv() {
        Path file = WORKDIR.resolve(".env");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                DOTENV.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("[env] could not read .env: " + e.getMessage());
        }
    }
}
