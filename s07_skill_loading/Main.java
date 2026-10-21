package s07_skill_loading;

import harness.AgentLoop;
import harness.Cli;
import harness.Env;
import harness.LlmClient;
import harness.Message;
import harness.Skills;
import harness.StandardTools;
import harness.ToolRegistry;
import harness.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * s07: Skill Loading
 *
 * The system prompt advertises only a skill catalog. The full body is read
 * through load_skill when the model decides the skill is relevant.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("""
                    s07: Skill Loading

                    Usage:
                      java -cp out s07_skill_loading.Main

                    Skills are folders containing SKILL.md. Set SKILLS_DIR to
                    override the default ./skills root.
                    """);
            return;
        }

        Cli.header("s07", "Skill Loading");

        Workspace workspace = new Workspace(Env.workdir());
        LlmClient client = LlmClient.fromEnv();
        Skills skills = new Skills(Path.of(Env.get("SKILLS_DIR", "skills")));
        skills.catalog();

        ToolRegistry tools = StandardTools.base(workspace, 120)
                .register(skills.listTool())
                .register(skills.loadTool());

        String system = StandardTools.systemPrompt(
                "a coding agent",
                workspace,
                "Skills are loaded on demand. Read the catalog below, then call "
                        + "load_skill for the one that matches the task.\n\n" + skills.catalogText());

        AgentLoop loop = new AgentLoop(client, tools, system)
                .listener(Cli.printingListener());

        List<Message.Msg> history = new ArrayList<>();
        Cli.repl("s07", loop, history, query -> {
            if (!query.equals("/skills")) {
                return false;
            }
            System.out.println(skills.catalogText());
            return true;
        });
    }
}
