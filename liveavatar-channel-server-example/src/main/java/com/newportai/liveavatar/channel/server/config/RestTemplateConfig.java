package com.newportai.liveavatar.channel.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${avatar.sandbox.enabled:false}")
    private boolean sandboxEnabled;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        if (sandboxEnabled) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("X-Env-Sandbox", "true");
                return execution.execute(request, body);
            });
        }
        return restTemplate;
    }
}
