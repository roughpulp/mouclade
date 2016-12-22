package rpulp.mouclade;

import com.google.common.base.Charsets;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

public class KeyboardInput {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardInput.class);

    private final OutputStream output;
    private final EventHandler<KeyEvent> onKeyTyped;

    public KeyboardInput(OutputStream output) {
        this.output = output;
        this.onKeyTyped = evt -> {
            String chars = evt.getCharacter();
            byte[] bytes = chars.getBytes(Charsets.UTF_8);
            try {
                if (bytes.length == 1 && bytes[0] == '\r') {
                    output.write('\n');
                } else {
                    output.write(bytes);
                }
                output.flush();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            LOGGER.info("typed, text: \""+ evt.getText() + "\", code: " + evt.getCode() + ", char: " + evt.getCharacter() + " / [" + toHexString(bytes) + "]");
        };
    }

    public EventHandler<KeyEvent> keyTypedHandler() { return onKeyTyped; }

    private String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int ii = 0; ii < bytes.length; ++ii) {
            sb.append(Integer.toHexString((bytes[ii] & 0xff) >>> 8 ));
            sb.append(Integer.toHexString(bytes[ii] & 0x0f) );
        }
        return sb.toString();
    }
}
