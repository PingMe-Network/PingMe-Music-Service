package org.ping_me.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Resilience4j circuit breaker
 * Used to handle Core Service availability issues gracefully
 * 
 * When Core Service is unavailable, the circuit breaker allows fallback
 * to token-based session validation instead of friendship checks
 */
@Configuration
@Slf4j
public class CircuitBreakerConfiguration {

    /**
     * Circuit breaker for Core Service friendship checks
     * 
     * States:
     * - CLOSED: Normal operation, calls go through
     * - OPEN: Too many failures detected, requests rejected immediately
     * - HALF_OPEN: Testing if service recovered, limited requests allowed
     */
    @Bean(name = "coreServiceCircuitBreaker")
    public CircuitBreaker coreServiceCircuitBreaker() {
        // Configuration
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Open circuit if 50% of recent calls fail
                .failureRateThreshold(50)
                // Or if 50% of recent calls are slow (> 2 seconds)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                // Wait 30 seconds before trying to recover (HALF_OPEN state)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // In HALF_OPEN state, allow only 3 test calls
                .permittedNumberOfCallsInHalfOpenState(3)
                // Minimum number of calls before calculating failure rate
                .minimumNumberOfCalls(10)
                // Sliding window type: TIME_BASED means last 60 seconds of calls
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(60)
                .build();

        // Create circuit breaker
        CircuitBreaker circuitBreaker = CircuitBreaker.of("core-service-check", config);

        // Log state transitions
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("[CircuitBreaker] Core Service: {} → {}", 
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onError(event -> {
                    log.debug("[CircuitBreaker] Core Service call failed: {}", 
                            event.getThrowable().getMessage());
                })
                .onSuccess(event -> {
                    log.debug("[CircuitBreaker] Core Service call succeeded");
                });

        return circuitBreaker;
    }
}
