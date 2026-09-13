package com.itsdev.payroll.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${api.base.url}")
    private String baseUrl;

    @Value("${api.key}")
    private String apiKey;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        String expectedHash = HashUtil.md5(apiKey);
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", expectedHash)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }
    
    
//    @Bean
//    public ApplicationRunner run(WebClient webClient) {
//        return args -> {
//            String response = webClient.get()
//                    .uri("https://jsonplaceholder.typicode.com/posts/1")
//                    .retrieve()
//                    .bodyToMono(String.class)
//                    .block();
//
//            log.info("API Response: {}", response);
//        };
//    }



    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("➡️ Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("⬅️ Response: {} {}", clientResponse.statusCode(), clientResponse.headers().asHttpHeaders().getFirst("Content-Type"));
            return Mono.just(clientResponse);
        });
    }
    
}

