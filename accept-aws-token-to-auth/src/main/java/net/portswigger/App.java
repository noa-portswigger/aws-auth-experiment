package net.portswigger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) throws Exception {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8080);
        
        server.setHandler(new org.eclipse.jetty.server.Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                response.setStatus(200);
                response.getHeaders().put("Content-Type", "text/html; charset=utf-8");
                
                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Simple Web Server</title>
                    </head>
                    <body>
                        <h1>Hello from Simple Web Server!</h1>
                        <p>Request URI: %s</p>
                        <p>Method: %s</p>
                    </body>
                    </html>
                    """.formatted(request.getHttpURI().getPath(), request.getMethod());
                
                response.write(true, java.nio.ByteBuffer.wrap(html.getBytes()), callback);
                return true;
            }
        });
        
        server.start();
        logger.info("Server started on http://localhost:8080");
        server.join();
    }
}