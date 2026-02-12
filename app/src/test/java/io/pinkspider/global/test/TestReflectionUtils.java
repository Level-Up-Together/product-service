package io.pinkspider.global.test;

import java.lang.reflect.Field;

public final class TestReflectionUtils {

    private TestReflectionUtils() {
    }

    public static void setId(Object entity, Object id) {
        setField(entity, "id", id);
    }

    public static void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
