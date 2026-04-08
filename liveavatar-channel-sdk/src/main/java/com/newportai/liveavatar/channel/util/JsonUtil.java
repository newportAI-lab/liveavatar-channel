package com.newportai.liveavatar.channel.util;

import com.newportai.liveavatar.channel.exception.MessageSerializationException;
import com.newportai.liveavatar.channel.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON utility for message serialization/deserialization
 */
public class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    /**
     * Serialize message to JSON string
     *
     * @param message message object
     * @return JSON string
     * @throws MessageSerializationException if serialization fails
     */
    public static String toJson(Message message) throws MessageSerializationException {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new MessageSerializationException("Failed to serialize message", e);
        }
    }

    /**
     * Serialize object to JSON string
     *
     * @param obj object
     * @return JSON string
     * @throws MessageSerializationException if serialization fails
     */
    public static String toJson(Object obj) throws MessageSerializationException {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new MessageSerializationException("Failed to serialize object", e);
        }
    }

    /**
     * Deserialize JSON string to message
     *
     * @param json JSON string
     * @return message object
     * @throws MessageSerializationException if deserialization fails
     */
    public static Message fromJson(String json) throws MessageSerializationException {
        try {
            return OBJECT_MAPPER.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            throw new MessageSerializationException("Failed to deserialize message", e);
        }
    }

    /**
     * Convert data object to specified type
     *
     * @param data      data object
     * @param valueType target type
     * @param <T>       type parameter
     * @return converted object
     */
    public static <T> T convertData(Object data, Class<T> valueType) {
        return OBJECT_MAPPER.convertValue(data, valueType);
    }
}
