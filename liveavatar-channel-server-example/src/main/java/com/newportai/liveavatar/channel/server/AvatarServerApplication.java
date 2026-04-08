package com.newportai.liveavatar.channel.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Live Avatar Channel Server Example Application
 *
 * This is a reference implementation of a developer server that implements
 * the Live Avatar Channel Protocol. It demonstrates how to:
 *
 * 1. Accept WebSocket connections from live avatar services
 * 2. Handle protocol messages (session, input, response, control, system)
 * 3. Process audio data and perform ASR (Automatic Speech Recognition)
 * 4. Send streaming responses back to the live avatar service
 * 5. Handle interrupts and idle triggers
 *
 * The server listens on ws://localhost:8080/avatar/ws
 */
@SpringBootApplication
public class AvatarServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvatarServerApplication.class, args);
        System.out.println("\n===========================================");
        System.out.println("Live Avatar Channel Server started successfully!");
        System.out.println("WebSocket endpoint: ws://localhost:8080/avatar/ws");
        System.out.println("===========================================\n");
    }
}
