package io.pinkspider.global.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.text.MessageFormat;

public class ObjectMapperUtils {

    private static ObjectMapper objectMapper = null;

    private static ObjectMapper mapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        }
        return objectMapper;
    }

    public static String toJson(Object object) {
        String result;
        try {
            result = mapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
        return result;
    }

    public static <T> T convertObject(String content, Class<T> clazz) {
        T object;
        try {
            object = mapper().readValue(content, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        return object;
    }

    public static <T> T convertObject(Object object, Class<T> clazz) {
        T obj;
        try {
            obj = mapper().readValue(toJson(object), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        return obj;
    }

    public static <T> T convertObjectList(Object object, TypeReference<T> typeRef) {
        T obj;
        try {
            obj = mapper().readValue(toJson(object), typeRef);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        return obj;
    }

    public static String fm(String format, Object... args) {
        return MessageFormat.format(format, args);
    }
}
