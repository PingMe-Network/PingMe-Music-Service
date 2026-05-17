package org.ping_me.config.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

    @Bean
    public RestClient coreServiceRestClient(@Value("${service.url.core}") String coreServiceUrl) {
        return RestClient.builder()
                .baseUrl(coreServiceUrl)
                .build();
    }
}

