package io.pinkspider.global.feign.admin;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "admin-internal-client",
    url = "${app.admin.api-url}",
    configuration = AdminInternalFeignConfig.class
)
public interface AdminInternalFeignClient {

    @GetMapping("/api/internal/featured-content/players")
    List<String> getFeaturedPlayerUserIds(@RequestParam("category_id") Long categoryId);

    @GetMapping("/api/internal/featured-content/guilds")
    List<Long> getFeaturedGuildIds(@RequestParam("category_id") Long categoryId);

    @GetMapping("/api/internal/featured-content/feeds")
    List<Long> getFeaturedFeedIds(@RequestParam("category_id") Long categoryId);

    @GetMapping("/api/internal/banners")
    List<AdminBannerDto> getActiveBanners();

    @GetMapping("/api/internal/banners/type/{bannerType}")
    List<AdminBannerDto> getBannersByType(@PathVariable("bannerType") String bannerType);
}
