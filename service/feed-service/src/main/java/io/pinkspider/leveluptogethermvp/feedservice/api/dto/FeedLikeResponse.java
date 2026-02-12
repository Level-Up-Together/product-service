package io.pinkspider.leveluptogethermvp.feedservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeedLikeResponse(
    @JsonProperty("is_liked")
    boolean isLiked,

    @JsonProperty("like_count")
    int likeCount
) {
}
