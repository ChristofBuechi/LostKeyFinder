package ch.lostkeyfinder.http

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val supplied = request.getHeader("X-Correlation-ID")
        val id = supplied?.takeIf { UUID_PATTERN.matches(it) } ?: UUID.randomUUID().toString()
        request.setAttribute("correlationId", id)
        response.setHeader("X-Correlation-ID", id)
        MDC.put("correlationId", id)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove("correlationId")
        }
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    }
}
