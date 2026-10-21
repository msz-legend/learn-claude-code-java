package harness;

import java.util.List;

/** An independent check at the loop's return boundary. */
public interface StopGate {
    Decision beforeStop(List<Message.Msg> history, String lastText) throws Exception;

    record Decision(boolean stop, String reason, String status) {
        public static Decision stop(String status) {
            return new Decision(true, null, status);
        }

        public static Decision continueWith(String reason) {
            return new Decision(false, reason, "working");
        }
    }
}
