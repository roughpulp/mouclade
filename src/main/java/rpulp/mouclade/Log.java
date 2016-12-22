package rpulp.mouclade;

import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.writers.ConsoleWriter;

public class Log {
    public static void init() {
        Configurator.defaultConfig()
                .writer(new ConsoleWriter())
                .formatPattern("{date:yyyy-MM-dd HH:mm:ss} - {level} [{thread}] {message}")
                .level(Level.DEBUG)
                .activate();
    }
}
