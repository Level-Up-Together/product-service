package io.pinkspider.global.domain.auditentity;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(value = {AuditingEntityListener.class})
@Getter
@Setter
@ToString
@RequiredArgsConstructor
abstract public class CreateOrModifiyByEntity {

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String modifiedBy;
}
