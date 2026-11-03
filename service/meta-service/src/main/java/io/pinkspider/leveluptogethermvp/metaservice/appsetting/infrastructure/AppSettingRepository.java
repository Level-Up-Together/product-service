package io.pinkspider.leveluptogethermvp.metaservice.appsetting.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {}
