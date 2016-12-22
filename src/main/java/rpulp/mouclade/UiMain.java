package rpulp.mouclade;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class UiMain extends Application{

    private static final Logger LOGGER = LoggerFactory.getLogger(UiMain.class);

    @Override
    public void start(Stage stage) throws Exception {
        loadIcon(stage);
        TerminalModel terminalModel = new TerminalModel();
        InputParser inputParser = new InputParser(terminalModel);
        TerminalView terminalView = new TerminalView(terminalModel);

        VBox vbox = new VBox();
        vbox.getChildren().addAll(terminalView.node());
        StackPane root = new StackPane();
        root.getChildren().add(vbox);
        Scene scene = new Scene(root);

        Proc proc = Proc.start(
                Config.NATIVE_COMMAND,
                (bytes, len) -> inputParser.parse(bytes, 0, len),
                (bytes, len) -> inputParser.parse(bytes, 0, len)
        );
        KeyboardInput keyboardInput = new KeyboardInput(proc.output());
        scene.setOnKeyTyped(keyboardInput.keyTypedHandler());

        Task waitForProcTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    LOGGER.info("proc waitfor ...");
                    proc.join();
                } catch (Exception ex) {
                    LOGGER.error("proc died: " + ex, ex);
                } finally {
                    LOGGER.info("proc done");
                }
                return null;
            }
        };
        Thread waitForProcThread = new Thread(waitForProcTask);
        waitForProcThread.setDaemon(true);
        waitForProcThread.start();

        stage.setTitle("Mouclade");
        stage.setScene(scene);
//        stage.setMaximized(true);
        stage.show();
    }

    private void loadIcon(Stage stage) {
        URL url = Thread.currentThread().getContextClassLoader().getResource("icon.1.64x64.png");
        if (url == null) {
            throw new RuntimeException("icon resource not found");
        }
        try {
            try (InputStream in = url.openStream()) {
                stage.getIcons().add(new Image(in));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void  main(String... args) throws Exception {
        Log.init();
        launch(args);
    }
}
