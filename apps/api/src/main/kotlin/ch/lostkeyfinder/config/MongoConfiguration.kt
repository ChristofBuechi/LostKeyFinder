package ch.lostkeyfinder.config

import com.mongodb.AuthenticationMechanism
import com.mongodb.ConnectionString
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager

@Configuration
@Profile("!offline")
class MongoConfiguration {
    @Bean
    fun mongoSettings(
        properties: ApiProperties,
        environment: Environment,
    ) = MongoClientSettingsBuilderCustomizer {
        val connection = ConnectionString(requireNotNull(environment.getProperty("spring.mongodb.uri")))
        if (properties.environment == "production") {
            val credential = connection.credential
            require(connection.sslEnabled == true && connection.isLoadBalanced == true && connection.retryWritesValue == false) {
                "Production MongoDB requires TLS, loadBalanced=true and retryWrites=false"
            }
            require(
                credential?.authenticationMechanism == AuthenticationMechanism.MONGODB_OIDC &&
                    credential.getMechanismProperty("ENVIRONMENT", "") == "gcp" &&
                    credential.getMechanismProperty("TOKEN_RESOURCE", "") == "FIRESTORE",
            ) {
                "Production MongoDB requires GCP OIDC for Firestore"
            }
        }
    }

    @Bean
    fun mongoTransactionManager(databaseFactory: MongoDatabaseFactory) = MongoTransactionManager(databaseFactory)
}
