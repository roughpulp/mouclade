package rpulp.mouclade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class Proc implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Proc.class);

    public interface InputListener {
        void onInput(byte[] bytes, int len);
    }

    private static class InputReader implements Runnable, AutoCloseable {

        private final InputStream in;
        private final byte[] bytes = new byte[1024];
        private final InputListener listener;
        private final Thread thread;

        private InputReader(InputStream in, InputListener listener) {
            this.in = in;
            this.listener = listener;
            this.thread = new Thread(this);
            this.thread.setDaemon(true);
            this.thread.start();
        }

        @Override
        public void close() throws Exception {
            thread.interrupt();
        }

        public void join() throws InterruptedException {
            this.thread.join();
        }

        @Override
        public void run() {
            LOGGER.info("InputReader started");
            try {
                runInner();
            } catch (Exception ex) {
                LOGGER.error("InputReader died " + ex, ex);
            } finally {
                LOGGER.info("InputReader stopped");
            }
        }

        private void runInner() throws IOException {
            for(;;) {
                final int len = in.read(bytes);
                if (len == -1) {
                    return;
                }
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append(new String(bytes, 0, len));
                    for (int ii = 0; ii < len; ++ii) {
                        if (ii % 20 == 0) {
                            sb.append("\n");
                        }
                        int byt = bytes[ii];
                        if (byt < 16) {
                            sb.append("0");
                        }
                        sb.append(Integer.toHexString(byt));
                    }
                    LOGGER.info(sb.toString());
                }
                listener.onInput(bytes, len);
            }
        }
    }

    public static Proc start(
            List<String> cmd,
            InputListener stdoutListener,
            InputListener stderrListener
    ) throws IOException {
        ProcessBuilder builder = new ProcessBuilder().command(cmd);
        buildPathEnv(builder);
        Process process = builder.start();
        LOGGER.info("proc started");
        return new Proc(
                process,
                new InputReader(process.getInputStream(), stdoutListener),
                new InputReader(process.getErrorStream(), stderrListener)
        );
    }

    private static void buildPathEnv(ProcessBuilder builder) {
        String path = System.getenv("PATH");
        path += Config.EXTRA_PATH;
        builder.environment().put("PATH", path);
    }

    private final Process process;
    private final InputReader stdoutReader;
    private final InputReader stderrReader;
    private final OutputStream output;

    public Proc(Process process, InputReader stdoutReader, InputReader stderrReader) {
        this.process = process;
        this.stdoutReader = stdoutReader;
        this.stderrReader = stderrReader;
        this.output = process.getOutputStream();
    }

    @Override
    public void close() throws Exception {
        try {
            process.destroy();
            process.waitFor();
        } finally {
            try {
                stdoutReader.close();
            } finally {
                stderrReader.close();
            }
        }
    }

    public Process process() { return process; }

    public OutputStream output() { return output; }

    public void join() throws InterruptedException {
        stdoutReader.join();
        stderrReader.join();
        process.waitFor();
    }
}
