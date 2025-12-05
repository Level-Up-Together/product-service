package io.pinkspider.leveluptogethermvp.userservice.core.component;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

//        int httpStatus = (ex instanceof JwtException) ? 401
//            : (ex instanceof BlacklistedJwtException) ? 401
//                : 401;

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final Map<String, Object> body = new HashMap<>();
        body.put("code", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("message", authException.getMessage());
        body.put("value", request.getServletPath());

        final ObjectMapper mapper = new ObjectMapper();
//        mapper.writeValue(response.getOutputStream(), body);
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
