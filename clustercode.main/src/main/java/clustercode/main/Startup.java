package clustercode.main;

import clustercode.api.config.ConfigLoader;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Startup {

    private static XLogger log;

    public static void main(String[] args) throws Exception {

        List<String> logFiles = Arrays.asList("log4j2.xml", System.getenv("CC_LOG_CONFIG_FILE"));

        logFiles.forEach(name -> {
            if (name == null) return;
            Path logFile = Paths.get(name);
            if (Files.exists(logFile)) {
                System.setProperty("log4j.configurationFile", logFile.toAbsolutePath().toString());
            }
        });
        log = XLoggerFactory.getXLogger(Startup.class);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Normally the expectable exceptions should be caught, but to debug any unexpected ones we log them.
            log.error("Application-wide uncaught exception:", throwable);
            System.exit(1);
        });

        log.info("Working dir: {}", new File("").getAbsolutePath());

        String configFile = args.length >= 1 ? args[0] : "conf/clustercode.properties";
        ConfigLoader loader = new ConfigLoader().loadDefaultsFromPropertiesFile(configFile);

        GuiceManager container = new GuiceManager(loader);

    }

}
