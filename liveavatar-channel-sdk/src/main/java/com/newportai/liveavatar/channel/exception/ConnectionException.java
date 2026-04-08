package com.newportai.liveavatar.channel.exception;

/**
 * Exception thrown when connection fails
 */
public class ConnectionException extends AvatarChannelException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
