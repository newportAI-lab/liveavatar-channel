package com.newportai.liveavatar.channel.exception;

/**
 * Exception thrown when message serialization/deserialization fails
 */
public class MessageSerializationException extends AvatarChannelException {

    public MessageSerializationException(String message) {
        super(message);
    }

    public MessageSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
