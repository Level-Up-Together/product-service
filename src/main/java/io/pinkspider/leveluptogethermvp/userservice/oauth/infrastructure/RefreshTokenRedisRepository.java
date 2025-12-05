package io.pinkspider.leveluptogethermvp.userservice.oauth.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.RefreshTokenRedisHash;
import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRedisRepository extends CrudRepository<RefreshTokenRedisHash, String> {

}
