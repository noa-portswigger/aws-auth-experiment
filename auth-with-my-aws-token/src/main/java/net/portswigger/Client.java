package net.portswigger;

import org.eclipse.jetty.client.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java HttpClient <url>");
            System.exit(1);
        }
        
        String url = args[0];
        
        org.eclipse.jetty.client.HttpClient client = new org.eclipse.jetty.client.HttpClient();
        
        try {
            client.start();
            
            ContentResponse response = client.GET(url);
            
            System.out.println(response.getContentAsString());
            
        } finally {
            client.stop();
        }
    }
}