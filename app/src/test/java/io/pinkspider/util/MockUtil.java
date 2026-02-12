package io.pinkspider.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;
import io.pinkspider.global.component.LmObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public class MockUtil {

    public static <T> T readJsonFileToClass(String jsonFileName, Class<T> ofClass) {
        try {
            File resource = new File(
                Objects.requireNonNull(MockUtil.class.getClassLoader().getResource(jsonFileName)).getFile());

            byte[] bytes = Files.readAllBytes(resource.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);

            LmObjectMapper lmObjectMapper = new LmObjectMapper();
            return lmObjectMapper.readValue(text, ofClass);
        } catch (Exception e) {
            // TODO Exception Handle
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T readJsonFileToClassList(String jsonFileName, TypeReference<T> typeRef) {
        try {
            File resource = new File(
                Objects.requireNonNull(MockUtil.class.getClassLoader().getResource(jsonFileName)).getFile());
            byte[] bytes = Files.readAllBytes(resource.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);

            LmObjectMapper lmObjectMapper = new LmObjectMapper();
            return lmObjectMapper.readValue(text, typeRef);
        } catch (Exception e) {
            // TODO Exception Handle
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T convertJsonToProjection(String jsonFileName, Class<T> ofClass) {
        try {
            File resource = new File(
                Objects.requireNonNull(MockUtil.class.getClassLoader().getResource(jsonFileName)).getFile());

            byte[] bytes = Files.readAllBytes(resource.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);

            LmObjectMapper lmObjectMapper = new LmObjectMapper();
            lmObjectMapper.registerModule(new MrBeanModule());
            return lmObjectMapper.readValue(text, ofClass);
        } catch (Exception e) {
            // TODO Exception Handle
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T convertJsonToProjectionList(String jsonFileName, TypeReference<T> typeRef) {
        try {
            File resource = new File(
                Objects.requireNonNull(MockUtil.class.getClassLoader().getResource(jsonFileName)).getFile());
            byte[] bytes = Files.readAllBytes(resource.toPath());
            String text = new String(bytes, StandardCharsets.UTF_8);

            LmObjectMapper lmObjectMapper = new LmObjectMapper();
            lmObjectMapper.registerModule(new MrBeanModule());
            return lmObjectMapper.readValue(text, typeRef);
        } catch (Exception e) {
            // TODO Exception Handle
            e.printStackTrace();
            return null;
        }
    }
}
