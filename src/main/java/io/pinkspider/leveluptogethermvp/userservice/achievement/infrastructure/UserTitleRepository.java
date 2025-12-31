package io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitlePosition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTitleRepository extends JpaRepository<UserTitle, Long> {

    @Query("SELECT ut FROM UserTitle ut JOIN FETCH ut.title WHERE ut.userId = :userId")
    List<UserTitle> findByUserIdWithTitle(@Param("userId") String userId);

    Optional<UserTitle> findByUserIdAndTitleId(String userId, Long titleId);

    @Query("SELECT ut FROM UserTitle ut JOIN FETCH ut.title WHERE ut.userId = :userId AND ut.isEquipped = true")
    Optional<UserTitle> findEquippedByUserId(@Param("userId") String userId);

    @Query("SELECT ut FROM UserTitle ut JOIN FETCH ut.title WHERE ut.userId = :userId AND ut.isEquipped = true")
    List<UserTitle> findEquippedTitlesByUserId(@Param("userId") String userId);

    @Query("SELECT ut FROM UserTitle ut JOIN FETCH ut.title WHERE ut.userId = :userId AND ut.isEquipped = true AND ut.equippedPosition = :position")
    Optional<UserTitle> findEquippedByUserIdAndPosition(@Param("userId") String userId, @Param("position") TitlePosition position);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserTitle ut SET ut.isEquipped = false, ut.equippedPosition = null WHERE ut.userId = :userId")
    void unequipAllByUserId(@Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserTitle ut SET ut.isEquipped = false, ut.equippedPosition = null WHERE ut.userId = :userId AND ut.equippedPosition = :position")
    void unequipByUserIdAndPosition(@Param("userId") String userId, @Param("position") TitlePosition position);

    @Query("SELECT COUNT(ut) FROM UserTitle ut WHERE ut.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    boolean existsByUserIdAndTitleId(String userId, Long titleId);
}
