package ch.lostkeyfinder

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

class MongoHealthFake : HealthIndicator {
    var ready = true

    override fun health(): Health = if (ready) Health.up().build() else Health.down().build()
}

@TestConfiguration(proxyBeanMethods = false)
class HealthFakeConfiguration {
    @Bean("mongoHealthContributor")
    fun mongoHealthFake() = MongoHealthFake()
}
