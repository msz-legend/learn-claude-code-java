package s14_mcp_plugin;

import harness.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A tiny MCP stdio server used by the s14 chapter. It speaks newline-delimited
 * JSON-RPC and exposes three tools: echo, add, time.
 */
public final class DemoMcpServer {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, Object> request;
            try {
                request = Json.object(Json.parse(line));
            } catch (RuntimeException e) {
                continue;
            }
            Object id = request.get("id");
            String method = Json.str(request, "method", "");
            if (id == null) {
                continue;
            }
            Map<String, Object> response = switch (method) {
                case "initialize" -> success(id, Json.obj(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Json.obj("tools", Json.obj()),
                        "serverInfo", Json.obj("name", "demo-mcp", "version", "1.0")));
                case "tools/list" -> success(id, Json.obj("tools", List.of(
                        Json.obj(
                                "name", "echo",
                                "description", "Echo the provided text.",
                                "inputSchema", Json.obj(
                                        "type", "object",
                                        "properties", Json.obj("text", Json.obj("type", "string")),
                                        "required", Json.list("text"))),
                        Json.obj(
                                "name", "add",
                                "description", "Add two numbers.",
                                "inputSchema", Json.obj(
                                        "type", "object",
                                        "properties", Json.obj(
                                                "a", Json.obj("type", "number"),
                                                "b", Json.obj("type", "number")),
                                        "required", Json.list("a", "b"))),
                        Json.obj(
                                "name", "time",
                                "description", "Return the current UTC time.",
                                "inputSchema", Json.obj("type", "object")))));
                case "tools/call" -> call(id, request);
                default -> error(id, -32601, "Unknown method: " + method);
            };
            out.println(Json.stringify(response));
        }
    }

    private static Map<String, Object> call(Object id, Map<String, Object> request) {
        Map<String, Object> params = Json.object(request.getOrDefault("params", Json.obj()));
        String name = Json.str(params, "name", "");
        Map<String, Object> arguments = Json.object(params.getOrDefault("arguments", Json.obj()));
        String text = switch (name) {
            case "echo" -> Json.str(arguments, "text", "");
            case "add" -> {
                double a = number(arguments.get("a"));
                double b = number(arguments.get("b"));
                double sum = a + b;
                yield sum == Math.rint(sum)
                        ? String.valueOf((long) sum)
                        : String.valueOf(sum);
            }
            case "time" -> Instant.now().toString();
            default -> null;
        };
        if (text == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        return success(id, Json.obj(
                "content", List.of(Json.obj("type", "text", "text", text)),
                "isError", false));
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Map<String, Object> success(Object id, Map<String, Object> result) {
        return Json.obj("jsonrpc", "2.0", "id", id, "result", result);
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        return Json.obj("jsonrpc", "2.0", "id", id,
                "error", Json.obj("code", code, "message", message));
    }
}
