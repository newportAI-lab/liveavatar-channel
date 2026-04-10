package com.newportai.liveavatar.channel.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Live Avatar Channel Server Example Application
 *
 * <p>This is a reference implementation of the <b>platform side</b> of the
 * Live Avatar Channel Protocol. It supports both connection modes:
 *
 * <h3>Inbound Mode (recommended for quick integration)</h3>
 * <ol>
 *   <li>Developer calls {@code POST /api/session/start} — receives {@code sessionId} and
 *       the WebSocket URL.</li>
 *   <li>Developer connects to the WebSocket URL and sends {@code session.init}.</li>
 *   <li>Platform responds with {@code session.ready}; normal protocol continues.</li>
 * </ol>
 * REST endpoint: {@code http://localhost:8080/api/session/start}
 *
 * <h3>Outbound Mode</h3>
 * <p>Developer hosts their own WebSocket server; the live avatar service connects to it.
 * The WebSocket handler in this example also serves as a reference for that mode.
 *
 * <p>WebSocket endpoint: {@code ws://localhost:8080/avatar/ws}
 */
@SpringBootApplication
public class AvatarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvatarServerApplication.class, args);
        System.out.println("\n===========================================");
        System.out.println("Live Avatar Channel Server started successfully!");
        System.out.println("WebSocket endpoint : ws://localhost:8080/avatar/ws");
        System.out.println("Inbound session API: POST http://localhost:8080/api/session/start");
        System.out.println("===========================================\n");
    }
}
