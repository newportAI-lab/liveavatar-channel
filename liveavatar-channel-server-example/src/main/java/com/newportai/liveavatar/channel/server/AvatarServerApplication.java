package com.newportai.liveavatar.channel.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Live Avatar Channel Server Example Application
 *
 * <p>Reference implementation of the developer side of the Live Avatar Channel Protocol
 * in WebSocket Agent mode. The platform hosts the WebSocket server; this application
 * connects as a client.
 *
 * <h3>Session Flow</h3>
 * <ol>
 *   <li>Call {@code POST /api/session/start} — proxies to the platform, receives
 *       {@code sessionId}, {@code agentWsUrl}, and frontend tokens.</li>
 *   <li>Connect to {@code agentWsUrl} via {@code AvatarAgent} — protocol
 *       starts with {@code session.init} sent to the platform.</li>
 *   <li>Reply with {@code session.ready}; normal protocol continues.</li>
 * </ol>
 *
 * <p>REST endpoints: {@code http://localhost:8080/api/session/start},
 * {@code http://localhost:8080/api/session/stop}
 */
@SpringBootApplication
public class AvatarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvatarServerApplication.class, args);
    }

    @Bean
    public CommandLineRunner printBanner(@Value("${server.port:8080}") int port) {
        return args -> {
            System.out.println("\n===========================================");
            System.out.println("Live Avatar Channel Server started successfully!");
            System.out.println("Session API: POST http://localhost:" + port + "/api/session/start");
            System.out.println("Session API: POST http://localhost:" + port + "/api/session/stop");
            System.out.println("===========================================\n");
        };
    }
}
