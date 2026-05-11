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
 * Live Avatar Channel Protocol. It supports two connection modes:
 *
 * <h3>Outbound Mode (default)</h3>
 * <p>Developer hosts their own WebSocket server; the platform connects to it directly.
 *
 * <h3>Inbound Mode</h3>
 * <ol>
 *   <li>Developer calls {@code POST /api/session/start} — receives {@code sessionId},
 *       {@code agentWsUrl}, and frontend tokens.</li>
 *   <li>Developer connects to {@code agentWsUrl} as a WebSocket client — protocol
 *       starts with {@code session.init} from the platform.</li>
 *   <li>Developer replies with {@code session.ready}; normal protocol continues.</li>
 * </ol>
 *
 * <p>WebSocket endpoint: {@code ws://localhost:8080/avatar/ws} (outbound mode only)<br>
 * REST endpoints: {@code http://localhost:8080/api/session/start},
 * {@code http://localhost:8080/api/session/stop}
 */
@SpringBootApplication
public class AvatarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvatarServerApplication.class, args);
    }

    @Bean
    public CommandLineRunner printBanner(@Value("${avatar.mode:outbound}") String mode,
                                          @Value("${server.port:8080}") int port) {
        return args -> {
            System.out.println("\n===========================================");
            System.out.println("Live Avatar Channel Server started successfully!");
            System.out.println("Mode: " + mode);
            System.out.println("WebSocket endpoint : ws://localhost:" + port + "/avatar/ws");
            System.out.println("Session API       : POST http://localhost:" + port + "/api/session/start");
            System.out.println("Session API       : POST http://localhost:" + port + "/api/session/stop");
            System.out.println("===========================================\n");
        };
    }
}
