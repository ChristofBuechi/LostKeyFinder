package ch.lostkeyfinder

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

class MongoHealthFake : HealthIndicator {
    var ready = true

    override fun health(): Health = if (ready) Health.up().build() else Health.down().build()
}

@TestConfiguration(proxyBeanMethods = false)
class HealthFakeConfiguration {
    @Bean("mongoHealthContributor")
    fun mongoHealthFake() = MongoHealthFake()

    @Bean
    @Order(0)
    fun contractTestSecurity(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/api/v1/test/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
