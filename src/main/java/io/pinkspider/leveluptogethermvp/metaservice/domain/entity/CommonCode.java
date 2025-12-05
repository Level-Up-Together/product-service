package io.pinkspider.leveluptogethermvp.metaservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Comment("공통코드 관리 테이블")
@Table(name = "common_code")
public class CommonCode {

    @Id
    @Size(max = 4)
    @Comment("M (Member)  I (Invest). B (Borrow). T (Ticket)  N (Note)  A (Admin)")
    @Column(name = "id", nullable = false, length = 4)
    private String id;

    @Size(max = 50)
    @Column(name = "code_name", length = 50)
    private String codeName;

    @Size(max = 50)
    @Column(name = "code_title", length = 50)
    private String codeTitle;

    @Size(max = 64)
    @Column(name = "description", length = 64)
    private String description;

    @Size(max = 4)
    @Comment("상위 그룹 아이디 : 비슷한 코드를 같은 그룹으로 묶기 위해")
    @Column(name = "parent_id", length = 4)
    private String parentId;
}
