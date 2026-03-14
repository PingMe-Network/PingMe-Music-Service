package org.ping_me;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableCaching
@EnableJpaRepositories(basePackages = "org.ping_me.repository.jpa")
@EnableAsync
@EnableMethodSecurity
public class PingMeMusicServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PingMeMusicServiceApplication.class, args);
    }

}
