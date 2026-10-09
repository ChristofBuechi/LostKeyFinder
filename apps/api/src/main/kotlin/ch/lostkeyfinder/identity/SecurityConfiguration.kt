package ch.lostkeyfinder.identity

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerExceptionResolver
import java.time.Instant
import java.util.UUID

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties::class)
class SecurityConfiguration {
    @Bean
    fun jwtDecoder(
        properties: IdentityProperties,
        environment: Environment,
    ): JwtDecoder {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            require(properties.supabaseUrl.scheme == "https" && properties.publishableKey.isNotBlank()) {
                "Production identity requires HTTPS and a Supabase publishable key"
            }
        }
        val decoder =
            NimbusJwtDecoder
                .withJwkSetUri("${properties.issuer}/.well-known/jwks.json")
                .jwsAlgorithm(properties.algorithm)
                .build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer),
                JwtClaimValidator<List<String>>("aud") { it.contains(properties.audience) },
                // JwtClaimValidator rejects absent claims before calling the predicate.
                JwtClaimValidator<Instant>("exp") { true },
                JwtClaimValidator<String>("sub") { value ->
                    runCatching { UUID.fromString(value).let { it.version() == 4 && it.toString() == value } }.getOrDefault(false)
                },
            ),
        )
        return decoder
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("handlerExceptionResolver") problems: HandlerExceptionResolver,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/v1/health/live", "/api/v1/health/ready", "/api/v1/version")
                    .permitAll()
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { resource ->
                resource.jwt { }
                resource.authenticationEntryPoint { request, response, _ ->
                    response.setHeader("WWW-Authenticate", "Bearer")
                    problems.resolveException(
                        request,
                        response,
                        null,
                        ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"),
                    )
                }
                resource.accessDeniedHandler { request, response, _ ->
                    problems.resolveException(request, response, null, ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
                }
            }
        return http.build()
    }
}
