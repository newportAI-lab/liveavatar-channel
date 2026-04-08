package com.newportai.liveavatar.channel.exception;

/**
 * Base exception for Live Avatar Channel SDK
 */
public class AvatarChannelException extends Exception {

    public AvatarChannelException(String message) {
        super(message);
    }

    public AvatarChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
