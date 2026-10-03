package ch.lostkeyfinder.http

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class ProblemDetailsAdvice : ResponseEntityExceptionHandler() {
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> = problemResponse(ex, (request as ServletWebRequest).request, statusCode, headers)

    @ExceptionHandler(Exception::class)
    fun handle(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val status = (exception as? ErrorResponse)?.statusCode ?: HttpStatusCode.valueOf(500)
        return problemResponse(exception, request, status, HttpHeaders())
    }

    private fun problemResponse(
        exception: Exception,
        request: HttpServletRequest,
        status: HttpStatusCode,
        headers: HttpHeaders,
    ): ResponseEntity<Any> {
        val (name, title) =
            names[status.value()] ?: if (status.is5xxServerError) {
                names.getValue(500)
            } else {
                "request-failed" to "Request failed"
            }
        val problem = ProblemDetail.forStatus(status)
        problem.type = URI("/problems/$name")
        problem.title = title
        problem.instance = URI(request.requestURI)
        problem.setProperty("correlationId", request.getAttribute("correlationId"))
        if (status.is4xxClientError && exception is ResponseStatusException) {
            problem.detail = exception.reason
        }
        if (exception is MethodArgumentNotValidException) {
            problem.detail = "Request validation failed"
            problem.setProperty("errors", exception.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" })
        }
        if (status.is5xxServerError) {
            problem.detail = null
            log.error(
                "request_failed method={} path={} status={} correlationId={} errorType={}",
                request.method,
                request.requestURI,
                status.value(),
                request.getAttribute("correlationId"),
                exception.javaClass.simpleName,
            )
        }
        val responseHeaders = HttpHeaders()
        responseHeaders.putAll(headers)
        responseHeaders.contentType = MediaType.APPLICATION_PROBLEM_JSON
        return ResponseEntity(problem, responseHeaders, status)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProblemDetailsAdvice::class.java)
        private val names =
            mapOf(
                400 to ("invalid-request" to "Invalid request"),
                401 to ("unauthorized" to "Unauthorized"),
                403 to ("forbidden" to "Forbidden"),
                404 to ("not-found" to "Not found"),
                409 to ("conflict" to "Conflict"),
                429 to ("rate-limit-exceeded" to "Too many requests"),
                500 to ("internal-server-error" to "Internal server error"),
                503 to ("dependency-unavailable" to "Service unavailable"),
            )
    }
}
