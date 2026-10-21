package s14_mcp_plugin;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.Json;
import harness.LlmClient;
import harness.Mcp;
import harness.Message;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * s14: MCP Plugin
 *
 * MCP tools are discovered at startup and exposed in the same registry as
 * local tools, namespaced as mcp__<server>__<tool>.
 *
 * mcp.json:
 * {
 *   "servers": {
 *     "demo": {"command": ["java", "-cp", "out", "s14_mcp_plugin.DemoMcpServer"]}
 *   }
 * }
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s14: MCP Plugin

                    Usage:
                      java -cp out s14_mcp_plugin.Main

                    Reads server commands from mcp.json. If the file is absent, the
                    bundled DemoMcpServer is started so the chapter works offline.
                    """);
            return;
        }

        Cli.header("s14", "MCP Plugin");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        ToolRegistry tools = StandardTools.base(workspace, 120);

        Map<String, List<String>> servers = loadServers();
        List<Mcp.Client> clients = new ArrayList<>();
        try {
            for (Map.Entry<String, List<String>> entry : servers.entrySet()) {
                try {
                    Mcp.Client mcp = Mcp.Client.start(entry.getValue(), workspace.root());
                    mcp.initialize();
                    int count = mcp.register(tools, entry.getKey());
                    clients.add(mcp);
                    System.out.println(Cli.color(
                            "[mcp] " + entry.getKey() + ": " + count + " tools", Cli.DIM));
                } catch (Exception e) {
                    System.out.println(Cli.color(
                            "[mcp] " + entry.getKey() + " failed: " + e.getMessage(), Cli.RED));
                }
            }

            String system = StandardTools.systemPrompt(
                    "a coding agent",
                    workspace,
                    "MCP tools are named mcp__<server>__<tool>. "
                            + "Use them like local tools; their schemas are authoritative.");

            AgentLoop loop = new AgentLoop(client, tools, system)
                    .listener(Cli.printingListener());

            List<Message.Msg> history = new ArrayList<>();
            Cli.repl("s14", loop, history, query -> {
                if (!query.equals("/mcp")) {
                    return false;
                }
                tools.all().keySet().stream()
                        .filter(name -> name.startsWith("mcp__"))
                        .forEach(name -> System.out.println("- " + name));
                return true;
            });
        } finally {
            for (Mcp.Client mcp : clients) {
                mcp.close();
            }
        }
    }

    private static Map<String, List<String>> loadServers() throws Exception {
        Path file = Path.of("mcp.json");
        Map<String, List<String>> servers = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            Map<String, Object> root = Json.object(Json.parse(
                    Files.readString(file, StandardCharsets.UTF_8)));
            Object raw = root.get("servers");
            if (raw instanceof Map<?, ?>) {
                for (Map.Entry<String, Object> entry : Json.object(raw).entrySet()) {
                    Map<String, Object> config = Json.object(entry.getValue());
                    List<String> command = new ArrayList<>();
                    for (Object part : Json.array(config.get("command"))) {
                        command.add(String.valueOf(part));
                    }
                    if (!command.isEmpty()) {
                        servers.put(entry.getKey(), command);
                    }
                }
            }
        }
        if (servers.isEmpty()) {
            servers.put("demo", List.of("java", "-cp", "out", "s14_mcp_plugin.DemoMcpServer"));
        }
        return servers;
    }
}
