package net.portswigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "app", mixinStandardHelpOptions = true, version = "1.0",
         description = "AWS Token to GCP Auth Server")
public class App implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    @Option(names = {"--check-token-against-gcp"}, description = "Check tokens against GCP instead of AWS (default: false)")
    private boolean checkTokenAgainstGcp = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8080);

        try (Handler handler = new Handler(checkTokenAgainstGcp)) {
            server.setHandler(handler);
            
            server.start();
            logger.info("Server started on http://localhost:8080");
            logger.info("Token validation mode: {}", checkTokenAgainstGcp ? "GCP" : "AWS");
            server.join();
            return 0;
        } catch (Exception e) {
            logger.error("Server failed to start", e);
            return 1;
        }
    }
}