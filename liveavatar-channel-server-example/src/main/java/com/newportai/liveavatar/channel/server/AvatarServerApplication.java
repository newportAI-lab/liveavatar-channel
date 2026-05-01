package com.newportai.liveavatar.channel.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Live Avatar Channel Server Example Application
 *
 * <p>This is a reference implementation of the developer side of the
 * Live Avatar Channel Protocol. It supports both connection modes:
 *
 * <h3>Inbound Mode (default)</h3>
 * <ol>
 *   <li>Developer calls {@code POST /api/session/start} — receives {@code sessionId},
 *       {@code agentWsUrl}, and frontend tokens.</li>
 *   <li>Developer connects to {@code agentWsUrl} as a WebSocket client — protocol
 *       starts with {@code session.init} from the platform.</li>
 *   <li>Developer replies with {@code session.ready}; normal protocol continues.</li>
 * </ol>
 *
 * <h3>Outbound Mode</h3>
 * <p>Developer hosts their own WebSocket server; the live avatar service connects to it.
 * Set {@code avatar.mode=outbound} to skip the token-based auth validation.
 *
 * <p>WebSocket endpoint: {@code ws://localhost:8080/avatar/ws}<br>
 * REST endpoint: {@code http://localhost:8080/api/session/start}
 */
@SpringBootApplication
public class AvatarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvatarServerApplication.class, args);
    }

    @Bean
    public CommandLineRunner printBanner(@Value("${avatar.mode:inbound}") String mode,
                                          @Value("${server.port:8080}") int port) {
        return args -> {
            System.out.println("\n===========================================");
            System.out.println("Live Avatar Channel Server started successfully!");
            System.out.println("Mode: " + mode);
            System.out.println("WebSocket endpoint : ws://localhost:" + port + "/avatar/ws");
            if ("inbound".equals(mode)) {
                System.out.println("Inbound session API: POST http://localhost:" + port + "/api/session/start");
            }
            System.out.println("===========================================\n");
        };
    }
}
