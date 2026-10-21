package harness;

import java.util.List;
import java.util.Map;

/**
 * A session-scoped stop gate. The worker proposes a stop by ending its turn;
 * a separate model call decides whether the goal is actually met.
 */
public final class Goal implements StopGate {
    private final String goal;
    private final LlmClient client;
    private final int maxEvaluations;
    private int evaluations;

    public Goal(String goal, LlmClient client) {
        this.goal = goal == null ? "" : goal.strip();
        this.client = client;
        this.maxEvaluations = Env.getInt("GOAL_MAX_TURNS", 12);
    }

    public String goal() {
        return goal;
    }

    public int evaluations() {
        return evaluations;
    }

    @Override
    public Decision beforeStop(List<Message.Msg> history, String lastText) throws Exception {
        if (goal.isBlank()) {
            return Decision.stop("completed");
        }
        evaluations++;
        if (evaluations > maxEvaluations) {
            return Decision.stop("max_turns");
        }

        String transcript = ContextCompactor.transcript(history, 60_000);
        String prompt = """
                Goal:
                %s

                Conversation so far:
                %s

                Decide whether the goal is complete. Use only concrete evidence in the
                conversation. Do not assume that an unreported command succeeded or that
                a planned step was executed. If the goal can no longer be completed,
                set impossible to true.

                Return JSON only:
                {"ok": true|false, "impossible": true|false, "reason": "one short sentence"}
                """.formatted(goal, transcript);

        Message.LlmResponse response = client.complete(
                "You are an independent goal evaluator. You have no tools. "
                        + "Judge the conversation, not the worker's intentions.",
                List.of(Message.user(prompt)),
                List.of());
        String text = response.text();
        Map<String, Object> parsed = extractObject(text);
        if (parsed == null) {
            return Decision.continueWith(
                    "The goal evaluator returned an unreadable decision. "
                            + "Continue working and report concrete verification results.");
        }

        boolean ok = Json.bool(parsed, "ok", false);
        boolean impossible = Json.bool(parsed, "impossible", false);
        String reason = Json.str(parsed, "reason", "").strip();
        if (ok) {
            return Decision.stop("goal_met");
        }
        if (impossible) {
            return Decision.stop("impossible");
        }
        if (reason.isEmpty()) {
            reason = "The goal is not met yet.";
        }
        return Decision.continueWith(
                "Goal check: " + reason
                        + "\nKeep working. Run the verification command and report its concrete result.");
    }

    public String statusLine() {
        return "goal=" + (goal.isBlank() ? "(none)" : goal)
                + " evaluations=" + evaluations + "/" + maxEvaluations;
    }

    private static Map<String, Object> extractObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return Json.object(Json.parse(text.substring(start, end + 1)));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
