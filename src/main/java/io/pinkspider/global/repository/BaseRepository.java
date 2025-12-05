package io.pinkspider.global.repository;

import java.io.Serializable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<M, I extends Serializable> extends JpaRepository<M, I>, JpaSpecificationExecutor<M> {

}
