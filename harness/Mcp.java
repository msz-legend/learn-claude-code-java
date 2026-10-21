package harness;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal MCP stdio client: JSON-RPC lines over a child process's stdin/stdout.
 * It discovers tools and exposes them in the same ToolRegistry as local tools.
 */
public final class Mcp {
    private Mcp() {
    }

    public record RemoteTool(String name, String description, Map<String, Object> inputSchema) {
    }

    public static final class Client implements AutoCloseable {
        private final Process process;
        private final BufferedWriter stdin;
        private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final AtomicInteger ids = new AtomicInteger();
        private final int timeoutSeconds;

        private Client(Process process) {
            this.process = process;
            this.stdin = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            this.timeoutSeconds = Env.getInt("MCP_TIMEOUT_SECONDS", 60);
            Thread reader = new Thread(() -> {
                try (BufferedReader stdout = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        lines.offer(line);
                    }
                } catch (IOException ignored) {
                    // Process exited.
                } finally {
                    lines.offer("__MCP_EOF__");
                }
            }, "mcp-reader");
            reader.setDaemon(true);
            reader.start();
        }

        public static Client start(List<String> command, Path cwd) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (cwd != null) {
                builder.directory(cwd.toFile());
            }
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            return new Client(builder.start());
        }

        public Map<String, Object> initialize() throws IOException {
            Map<String, Object> response = request("initialize", Json.obj(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Json.obj(),
                    "clientInfo", Json.obj(
                            "name", "learn-claude-code-java",
                            "version", "1.0")));
            notify("notifications/initialized", Json.obj());
            return response;
        }

        public List<RemoteTool> listTools() throws IOException {
            Map<String, Object> result = request("tools/list", Json.obj());
            List<RemoteTool> tools = new ArrayList<>();
            Object raw = result.get("tools");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?>)) {
                        continue;
                    }
                    Map<String, Object> map = Json.object(item);
                    Object schema = map.containsKey("inputSchema") ? map.get("inputSchema") : map.get("input_schema");
                    tools.add(new RemoteTool(
                            Json.str(map, "name", ""),
                            Json.str(map, "description", ""),
                            schema instanceof Map<?, ?> ? Json.object(schema) : Json.obj("type", "object")));
                }
            }
            return tools;
        }

        public String callTool(String name, Map<String, Object> arguments) throws IOException {
            Map<String, Object> result = request("tools/call", Json.obj(
                    "name", name,
                    "arguments", arguments == null ? Map.of() : arguments));
            StringBuilder out = new StringBuilder();
            Object content = result.get("content");
            if (content instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?>) {
                        Map<String, Object> block = Json.object(item);
                        if ("text".equals(Json.str(block, "type", ""))) {
                            if (!out.isEmpty()) {
                                out.append('\n');
                            }
                            out.append(Json.str(block, "text", ""));
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                out.append(Json.stringify(result));
            }
            boolean error = Json.bool(result, "isError", false);
            return error ? "Error: " + out : out.toString();
        }

        /** Register every remote tool as mcp__<server>__<tool>. */
        public int register(ToolRegistry registry, String serverName) throws IOException {
            List<RemoteTool> remote = listTools();
            for (RemoteTool tool : remote) {
                String exposed = "mcp__" + safe(serverName) + "__" + safe(tool.name());
                Map<String, Object> schema = tool.inputSchema() == null || tool.inputSchema().isEmpty()
                        ? Json.obj("type", "object")
                        : tool.inputSchema();
                registry.register(new Tool(
                        exposed,
                        tool.description().isBlank() ? "MCP tool " + tool.name() : tool.description(),
                        schema,
                        input -> callTool(tool.name(), input)));
            }
            return remote.size();
        }

        public boolean alive() {
            return process.isAlive();
        }

        private Map<String, Object> request(String method, Map<String, Object> params) throws IOException {
            int id = ids.incrementAndGet();
            Map<String, Object> request = Json.obj(
                    "jsonrpc", "2.0",
                    "id", id,
                    "method", method);
            if (params != null) {
                request.put("params", params);
            }
            write(request);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                String line;
                try {
                    line = lines.poll(Math.max(1, remaining), TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for MCP response", e);
                }
                if (line == null) {
                    break;
                }
                if (line.equals("__MCP_EOF__")) {
                    throw new IOException("MCP server closed its output");
                }
                Map<String, Object> message;
                try {
                    message = Json.object(Json.parse(line));
                } catch (RuntimeException e) {
                    continue;
                }
                if (!message.containsKey("id") || !String.valueOf(id).equals(String.valueOf(message.get("id")))) {
                    continue;
                }
                Object error = message.get("error");
                if (error instanceof Map<?, ?>) {
                    Map<String, Object> errorMap = Json.object(error);
                    throw new IOException("MCP error " + Json.str(errorMap, "code", "") + ": "
                            + Json.str(errorMap, "message", ""));
                }
                Object result = message.get("result");
                return result instanceof Map<?, ?> ? Json.object(result) : Map.of();
            }
            throw new IOException("MCP request timed out: " + method);
        }

        private void notify(String method, Map<String, Object> params) throws IOException {
            Map<String, Object> notification = Json.obj("jsonrpc", "2.0", "method", method);
            if (params != null) {
                notification.put("params", params);
            }
            write(notification);
        }

        private synchronized void write(Map<String, Object> message) throws IOException {
            stdin.write(Json.stringify(message));
            stdin.newLine();
            stdin.flush();
        }

        @Override
        public void close() {
            try {
                stdin.close();
            } catch (IOException ignored) {
                // Already closed.
            }
            process.destroy();
        }

        private static String safe(String name) {
            String value = name == null ? "server" : name.replaceAll("[^a-zA-Z0-9_]", "_");
            return value.isBlank() ? "server" : value;
        }
    }
}
