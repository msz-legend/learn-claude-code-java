package harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A tiny plan the model can rewrite as work progresses. */
public final class Todo {
    public record Item(String id, String content, String status) {
    }

    private final List<Item> items = new ArrayList<>();

    public synchronized List<Item> items() {
        return List.copyOf(items);
    }

    public synchronized String write(Object rawTodos) {
        List<Object> raw;
        try {
            if (rawTodos instanceof String text) {
                raw = Json.array(Json.parse(text));
            } else {
                raw = Json.array(rawTodos);
            }
        } catch (RuntimeException e) {
            return "Error: todos must be an array of {content, status} objects";
        }

        List<Item> next = new ArrayList<>();
        String error = null;
        int inProgress = 0;
        int index = 0;
        for (Object value : raw) {
            index++;
            if (!(value instanceof Map<?, ?>)) {
                error = "Error: todo #" + index + " is not an object";
                break;
            }
            Map<String, Object> map = Json.object(value);
            String content = Json.str(map, "content", Json.str(map, "text", "")).strip();
            if (content.isEmpty()) {
                error = "Error: todo #" + index + " has no content";
                break;
            }
            String status = normalizeStatus(Json.str(map, "status", "pending"));
            if (status == null) {
                error = "Error: todo #" + index + " has an invalid status";
                break;
            }
            if (status.equals("in_progress")) {
                inProgress++;
            }
            String id = Json.str(map, "id", "todo-" + index);
            next.add(new Item(id, content, status));
        }
        if (error != null) {
            return error;
        }
        if (inProgress > 1) {
            return "Error: only one todo may be in_progress";
        }
        items.clear();
        items.addAll(next);
        return render();
    }

    public synchronized String render() {
        if (items.isEmpty()) {
            return "(no todos)";
        }
        StringBuilder out = new StringBuilder();
        int done = 0;
        for (Item item : items) {
            if (item.status().equals("completed")) {
                done++;
            }
            String mark = switch (item.status()) {
                case "completed" -> "[x]";
                case "in_progress" -> "[>]";
                default -> "[ ]";
            };
            out.append(mark).append(' ').append(item.content()).append('\n');
        }
        out.append('(').append(done).append('/').append(items.size()).append(" completed)");
        return out.toString();
    }

    public Tool tool() {
        return new Tool(
                "todo_write",
                "Create or replace the task plan. Use statuses pending, in_progress, completed. "
                        + "Keep at most one item in_progress.",
                Json.obj(
                        "type", "object",
                        "properties", Json.obj(
                                "todos", Json.obj(
                                        "type", "array",
                                        "items", Json.obj(
                                                "type", "object",
                                                "properties", Json.obj(
                                                        "content", Json.obj("type", "string"),
                                                        "status", Json.obj(
                                                                "type", "string",
                                                                "enum", Json.list("pending", "in_progress", "completed"))),
                                                "required", Json.list("content", "status")))),
                        "required", Json.list("todos")),
                input -> write(input.get("todos")));
    }

    public static Map<String, Object> asJson(Item item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.id());
        map.put("content", item.content());
        map.put("status", item.status());
        return map;
    }

    private static String normalizeStatus(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "pending", "todo", "not_started" -> "pending";
            case "in_progress", "active", "doing" -> "in_progress";
            case "completed", "complete", "done" -> "completed";
            default -> null;
        };
    }
}
