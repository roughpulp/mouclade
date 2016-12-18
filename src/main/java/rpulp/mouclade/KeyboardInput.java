package rpulp.mouclade;

import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class KeyboardInput {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardInput.class);

    private final OutputStream output;
    private final EventHandler<KeyEvent> onKeyPressed;
    private final EventHandler<KeyEvent> onKeyReleased;

    public KeyboardInput(OutputStream output) {
        this.output = output;
        this.onKeyPressed = evt -> {
//            LOGGER.info("text: " + Arrays.toString(evt.getText().getBytes()) + ", code: " + evt.getCode());
        };
        this.onKeyReleased = evt -> {
            byte[] bytes = evt.getText().getBytes();
            try {
                output.write(bytes);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            LOGGER.info("text: " +  Arrays.toString(bytes) + ", code: " + evt.getCode());
        };
    }

    public EventHandler<KeyEvent> keyPressedHandler() { return onKeyPressed; }

    public EventHandler<KeyEvent> keyReleasedHandler() { return onKeyReleased; }
}
