package net.portswigger;

import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8080);
        
        try (TokenExchangeHandler handler = new TokenExchangeHandler()) {
            server.setHandler(handler);
            
            server.start();
            logger.info("Server started on http://localhost:8080");
            server.join();
        }
    }
}