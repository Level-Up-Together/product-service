package io.pinkspider.leveluptogethermvp.userservice.core.filter;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.userservice.core.api.UserApiStatus;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.AccessTokenNotValidException;
import io.pinkspider.leveluptogethermvp.userservice.core.exception.custom.CustomMissingRequestHeaderException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class ExceptionHandlerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IllegalStateException {
        try {
            filterChain.doFilter(request, response);
        } catch (AccessTokenNotValidException e) {
            // ErrorResponse를 사용하므로 errorCode값은 3자리 int 여야 한다. 그러므로 HttpStatus의 코드를 사용하였다.
            setErrorResponse(response, HttpStatus.NOT_ACCEPTABLE.value(), UserApiStatus.NOT_VALID_ACCESS_TOKEN.getResultMessage());
        } catch (ServletException | IllegalStateException | CustomMissingRequestHeaderException e) {
            setErrorResponse(response, HttpStatus.NOT_ACCEPTABLE.value(), UserApiStatus.NOT_EXIST_TOKEN.getResultMessage());
        } catch (Exception e) {
            setErrorResponse(response, HttpStatus.FORBIDDEN.value(), e.getMessage());
        }
    }

    private void setErrorResponse(HttpServletResponse response, int errorCode, String errorMessage) {
        ObjectMapper objectMapper = new ObjectMapper();
        response.setStatus(errorCode);
        response.setContentType(APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = new ErrorResponse(errorCode, errorMessage);
        try {
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } catch (IOException e) {
        }
    }

    @Data
    private static class ErrorResponse {

        private final int code;
        private final String message;
    }
}
