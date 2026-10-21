package harness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Cross-platform shell execution with a timeout and an output cap. */
public final class Shell {
    private static final int MAX_BYTES = 2_000_000;

    private Shell() {
    }

    public record Result(String output, int exitCode, boolean timedOut) {
    }

    public static Result run(String command, Path cwd, int timeoutSeconds) throws IOException, InterruptedException {
        return run(command, cwd, timeoutSeconds, Map.of());
    }

    public static Result run(String command, Path cwd, int timeoutSeconds, Map<String, String> extraEnv)
            throws IOException, InterruptedException {
        List<String> argv = Env.isWindows()
                ? List.of("cmd.exe", "/d", "/s", "/c", command)
                : List.of("/bin/sh", "-c", command);

        ProcessBuilder builder = new ProcessBuilder(argv);
        if (cwd != null) {
            builder.directory(cwd.toFile());
        }
        builder.environment().putAll(extraEnv);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        ByteCollector collector = new ByteCollector(process.getInputStream());
        Thread reader = new Thread(collector, "shell-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        if (!finished) {
            // Kill the whole tree: on Windows an orphaned grandchild would keep
            // the stdout pipe open, and the reader thread with it.
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            reader.join(500);
            return new Result(collector.text() + "\n[timed out after " + timeoutSeconds + "s]", -1, true);
        }
        reader.join(1000);
        return new Result(collector.text(), process.exitValue(), false);
    }

    private static final class ByteCollector implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        ByteCollector(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int read;
                while ((read = input.read(chunk)) != -1) {
                    if (buffer.size() < MAX_BYTES) {
                        buffer.write(chunk, 0, Math.min(read, MAX_BYTES - buffer.size()));
                    } else {
                        truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // Process was killed or the stream closed.
            }
        }

        String text() {
            String decoded = decode(buffer.toByteArray());
            String trimmed = decoded.strip();
            if (truncated) {
                trimmed += "\n... (output truncated)";
            }
            return trimmed;
        }
    }

    private static String decode(byte[] bytes) {
        // Prefer UTF-8. If the bytes are not valid UTF-8, fall back to the
        // platform encoding (for example GBK on a Chinese Windows console).
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            String nativeEncoding = System.getProperty("native.encoding");
            Charset charset = StandardCharsets.UTF_8;
            if (nativeEncoding != null && !nativeEncoding.isBlank()) {
                try {
                    charset = Charset.forName(nativeEncoding);
                } catch (Exception ignoredAgain) {
                    charset = Charset.defaultCharset();
                }
            }
            return new String(bytes, charset);
        }
    }
}
