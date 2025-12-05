package io.pinkspider.global.domain.auditentity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@MappedSuperclass
@Getter
@Setter
@ToString
@RequiredArgsConstructor
abstract public class ZonedDatatimeBaseEntity {

    private ZonedDateTime createdAt;
    private ZonedDateTime modifiedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = ZonedDateTime.now();
        this.modifiedAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.modifiedAt = ZonedDateTime.now();
    }
}

