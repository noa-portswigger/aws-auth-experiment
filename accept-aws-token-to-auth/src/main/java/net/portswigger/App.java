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

    @Option(names = {"--audience"}, description = "Expected audience for token validation (required when not using --check-token-against-gcp)")
    private String audience;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // Validate that audience is provided when not using GCP mode
        if (!checkTokenAgainstGcp && (audience == null || audience.trim().isEmpty())) {
            logger.error("--audience parameter is required when not using --check-token-against-gcp");
            return 1;
        }

        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8080);

        try (Handler handler = new Handler(checkTokenAgainstGcp, audience)) {
            server.setHandler(handler);
            
            server.start();
            logger.info("Server started on http://localhost:8080");
            logger.info("Token validation mode: {}", checkTokenAgainstGcp ? "GCP" : "AWS");
            if (!checkTokenAgainstGcp) {
                logger.info("Expected audience: {}", audience);
            }
            server.join();
            return 0;
        } catch (Exception e) {
            logger.error("Server failed to start", e);
            return 1;
        }
    }
}