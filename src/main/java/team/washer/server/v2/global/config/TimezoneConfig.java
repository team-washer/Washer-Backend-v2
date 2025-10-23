package team.washer.server.v2.global.config;

import java.time.LocalDateTime;
import java.util.TimeZone;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
class TimezoneConfig {

    @PostConstruct
    public void setDefaultTimezone() {
        System.setProperty("user.timezone", "Asia/Seoul");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        log.info("Default timezone set to {}: {}", TimeZone.getDefault().getID(), LocalDateTime.now());
    }
}
