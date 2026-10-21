package s05_todo_write;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.Hooks;
import harness.LlmClient;
import harness.Message;
import harness.StandardTools;
import harness.Todo;
import harness.ToolRegistry;
import harness.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * s05: TodoWrite
 *
 * The plan lives in the harness, not only in the prompt. A small hook counts
 * tool rounds since the last todo_write and appends a reminder after three.
 */
public final class Main {
    private static final int REMIND_AFTER = 3;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s05: TodoWrite

                    Usage:
                      java -cp out s05_todo_write.Main

                    The todo_write tool stores a plan and the loop nudges the model
                    when the plan has not been updated for three tool rounds.
                    """);
            return;
        }

        Cli.header("s05", "TodoWrite");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        Todo todo = new Todo();
        ToolRegistry tools = StandardTools.base(workspace, 120).register(todo.tool());

        AtomicInteger sinceTodo = new AtomicInteger();

        Hooks reminder = new Hooks() {
            @Override
            public String preToolUse(Message.Block.ToolUse call) {
                if (call.name().equals("todo_write")) {
                    sinceTodo.set(0);
                }
                return null;
            }

            @Override
            public String postToolUse(Message.Block.ToolUse call, String output) {
                if (call.name().equals("todo_write")) {
                    return output;
                }
                if (sinceTodo.incrementAndGet() >= REMIND_AFTER) {
                    sinceTodo.set(0);
                    return output + "\n\n<reminder>Update todo_write before continuing. "
                            + "Current plan:\n" + todo.render() + "</reminder>";
                }
                return output;
            }
        };

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Before a multi-step task, call todo_write with the full plan. "
                        + "Keep exactly one item in_progress and update it as you finish steps.");

        AgentLoop loop = new AgentLoop(client, tools, system)
                .hooks(reminder)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s05", loop, history, query -> {
            if (!query.equals("/todo")) {
                return false;
            }
            System.out.println(todo.render());
            return true;
        });
    }
}
