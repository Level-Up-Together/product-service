package io.pinkspider.leveluptogethermvp.metaservice.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.CommonCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CommonCodeRepository extends JpaRepository<CommonCode, String> {

    @Query("SELECT cc FROM CommonCode cc ORDER BY cc.id")
    List<CommonCode> retrieveAllCommonCode();

    /**
     * 상위 그룹 ID로 하위 코드 조회
     */
    @Query("SELECT cc FROM CommonCode cc WHERE cc.parentId = :parentId ORDER BY cc.id")
    List<CommonCode> findByParentId(String parentId);

    /**
     * 상위 그룹만 조회 (parentId가 null인 것들)
     */
    @Query("SELECT cc FROM CommonCode cc WHERE cc.parentId IS NULL ORDER BY cc.id")
    List<CommonCode> findAllParentCodes();
}
