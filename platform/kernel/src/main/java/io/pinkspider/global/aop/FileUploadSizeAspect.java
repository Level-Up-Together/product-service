package io.pinkspider.global.aop;

import io.pinkspider.global.annotation.LimitFileSize;
import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.ClientUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Iterator;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Aspect
@Component
@RequiredArgsConstructor
@RefreshScope
public class FileUploadSizeAspect {

    private final ClientUtils clientUtils;
    private final Environment environment;

    private static final String FILE_SIZE_KEY_PREFIX = "file-upload.max-file-size.";

    @Before("@annotation(io.pinkspider.global.annotation.LimitFileSize)")
    public void runningTime(JoinPoint joinPoint) throws Throwable {

        HttpServletRequest request = clientUtils.getRequest();
        String httpMethod = request.getMethod();

        if ("GET".equalsIgnoreCase(httpMethod) || "DELETE".equalsIgnoreCase(httpMethod)) {
            return;
        }

        if (!clientUtils.isMultipartRequest()) {
            return;
        }

        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        LimitFileSize limitFileSize = methodSignature.getMethod()
            .getAnnotation(LimitFileSize.class);

        String serviceKey = limitFileSize.serviceKey();

        if (ObjectUtils.isEmpty(serviceKey)) {
            serviceKey = "default";
        }

        long maxFileSize = Long.parseLong(environment.getProperty(FILE_SIZE_KEY_PREFIX + serviceKey));

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        Iterator<String> fileNames = multipartRequest.getFileNames();
        while (fileNames.hasNext()) {
            String key = fileNames.next();
            MultipartFile multipartFile = multipartRequest.getFile(key);

            if (multipartFile.getSize() > maxFileSize) {
                throw new CustomException(ApiStatus.EXCEEDED_FILE_SIZE.getResultCode(),
                    ApiStatus.EXCEEDED_FILE_SIZE.getResultMessage());
            }
        }
    }
}
