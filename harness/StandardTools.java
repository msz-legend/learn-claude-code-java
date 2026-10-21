package harness;

/** bash + the four file tools used from s02 onward. */
public final class StandardTools {
    private StandardTools() {
    }

    public static ToolRegistry base(Workspace workspace, int timeoutSeconds) {
        return new ToolRegistry()
                .register(bash(workspace, timeoutSeconds))
                .register(readFile(workspace))
                .register(writeFile(workspace))
                .register(editFile(workspace))
                .register(glob(workspace));
    }

    public static String systemPrompt(String role, Workspace workspace, String extra) {
        return "You are " + role + " at " + workspace.root() + ". "
                + "Environment: " + Env.environmentPrompt() + ". "
                + (extra == null ? "" : extra + " ")
                + "Act, don't explain.";
    }

    public static Tool bash(Workspace workspace, int timeoutSeconds) {
        return new Tool(
                "bash",
                "Run a shell command in the workspace.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("command", Json.obj("type", "string")),
                        "required", Json.list("command")),
                input -> {
                    String command = Json.str(input, "command", "").strip();
                    if (command.isEmpty()) {
                        return "Error: command is required";
                    }
                    if (Permission.isDangerous(command)) {
                        return "Error: Dangerous command blocked";
                    }
                    Shell.Result result = Shell.run(command, workspace.root(), timeoutSeconds);
                    String output = result.output();
                    if (result.timedOut()) {
                        return "Error: Timeout (" + timeoutSeconds + "s)\n" + output;
                    }
                    if (output.isBlank()) {
                        output = "(no output)";
                    }
                    if (output.length() > 50_000) {
                        output = output.substring(0, 50_000) + "\n... (output truncated)";
                    }
                    if (result.exitCode() != 0) {
                        output += "\n[exit code: " + result.exitCode() + "]";
                    }
                    return output;
                });
    }

    public static Tool readFile(Workspace workspace) {
        return new Tool(
                "read_file",
                "Read a UTF-8 text file. Optionally return only the first N lines.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "path", Json.obj("type", "string"),
                                "limit", Json.obj("type", "integer")),
                        "required", Json.list("path")),
                input -> {
                    String path = Json.str(input, "path", "");
                    Integer limit = input.get("limit") instanceof Number number ? number.intValue() : null;
                    return workspace.read(path, limit);
                });
    }

    public static Tool writeFile(Workspace workspace) {
        return new Tool(
                "write_file",
                "Write content to a file, creating parent directories when needed.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "path", Json.obj("type", "string"),
                                "content", Json.obj("type", "string")),
                        "required", Json.list("path", "content")),
                input -> workspace.write(
                        Json.str(input, "path", ""),
                        Json.str(input, "content", "")));
    }

    public static Tool editFile(Workspace workspace) {
        return new Tool(
                "edit_file",
                "Replace the first exact occurrence of old_text with new_text.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "path", Json.obj("type", "string"),
                                "old_text", Json.obj("type", "string"),
                                "new_text", Json.obj("type", "string")),
                        "required", Json.list("path", "old_text", "new_text")),
                input -> workspace.edit(
                        Json.str(input, "path", ""),
                        Json.str(input, "old_text", ""),
                        Json.str(input, "new_text", "")));
    }

    public static Tool glob(Workspace workspace) {
        return new Tool(
                "glob",
                "Find files matching a glob pattern; ** matches recursively.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj("pattern", Json.obj("type", "string")),
                        "required", Json.list("pattern")),
                input -> workspace.glob(Json.str(input, "pattern", "")));
    }
}
