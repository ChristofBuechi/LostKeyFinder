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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerExceptionResolver
import java.time.Duration
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties::class)
class SecurityConfiguration {
    @Bean
    fun jwtDecoder(
        properties: IdentityProperties,
        environment: Environment,
    ): JwtDecoder {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            require(
                properties.apiKey.isNotBlank() &&
                    properties.accountsUrl.toString() == "https://identitytoolkit.googleapis.com" &&
                    properties.jwkSetUri.toString() == IdentityProperties().jwkSetUri.toString(),
            ) {
                "Production identity requires a Firebase API key and Google verification endpoints"
            }
        }
        val decoder =
            NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri.toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer),
                JwtClaimValidator<List<String>>("aud") { it.contains(properties.projectId) },
                // JwtClaimValidator rejects absent claims before calling the predicate.
                JwtClaimValidator<Instant>("exp") { true },
                JwtClaimValidator<Instant>("iat") { !it.isAfter(Instant.now()) },
                JwtClaimValidator<Any>("auth_time") { value ->
                    value is Number && value.toLong() in
                        (Instant.now().epochSecond - Duration.ofDays(30).seconds + 1)..Instant.now().epochSecond
                },
                JwtClaimValidator<String>("sub") { value ->
                    value.isNotBlank() && value.length <= 128
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
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/tags/*")
                    .permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/public/tags/*/registration-drafts")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/v1/public/tags/*/finder-drafts",
                        "/api/v1/internal/chat-notifications/dispatch",
                        "/api/v1/internal/account-deletions/dispatch",
                    ).permitAll()
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { resource ->
                resource.bearerTokenResolver { request ->
                    if (request.requestURI in
                        setOf("/api/v1/internal/chat-notifications/dispatch", "/api/v1/internal/account-deletions/dispatch")
                    ) {
                        null
                    } else {
                        org.springframework.security.oauth2.server.resource.web
                            .DefaultBearerTokenResolver()
                            .resolve(request)
                    }
                }
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
