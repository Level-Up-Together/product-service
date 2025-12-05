package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.apple;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AppleUserDto {
    private Name name;
    private String email;

    @Data
    public static class Name {
        @JsonProperty("firstName")
        private String firstName;

        @JsonProperty("lastName")
        private String lastName;
    }
}
