package rpulp.mouclade;

import com.google.common.collect.Lists;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.writers.ConsoleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UiMain extends Application{

    private static final Logger LOGGER = LoggerFactory.getLogger(UiMain.class);

    private static void initLogging() {
        Configurator.defaultConfig()
                .writer(new ConsoleWriter())
                .formatPattern("{date:yyyy-MM-dd HH:mm:ss} - {level} [{thread}] {message}")
                .level(Level.DEBUG)
                .activate();
    }

    @Override
    public void start(Stage stage) throws Exception {

        Text text = new Text("coucou");

        VBox vbox = new VBox();
        vbox.getChildren().addAll(text);
        StackPane root = new StackPane();
        root.getChildren().add(vbox);
        Scene scene = new Scene(root);

        Thread thread = new Thread(() -> {
            try {
                Proc proc = Proc.start(
                        Lists.newArrayList(Config.NATIVE_EXE_PATH),
                        (bytes, len) -> {
                            text.setText(new String(bytes, 0, len));
                        },
                        (bytes, len) -> {
                            text.setText(new String(bytes, 0, len));
                        }
                );

                proc.output().write("ls -lah \r\n".getBytes());

                KeyboardInput keyboardInput = new KeyboardInput(proc.output());
                scene.setOnKeyPressed(keyboardInput.keyPressedHandler());
                scene.setOnKeyReleased(keyboardInput.keyReleasedHandler());

                LOGGER.info("proc waitfor ...");
                proc.join();
            } catch (Exception ex) {
                LOGGER.error("proc died: " + ex, ex);
            } finally {
                LOGGER.info("proc done");
            }
        });
        thread.setDaemon(true);
        thread.start();

        stage.setTitle("Mouclade");
        stage.setScene(scene);
//        stage.setMaximized(true);
        stage.show();
    }

    public static void  main(String... args) throws Exception {
        initLogging();
        launch(args);
    }
}
