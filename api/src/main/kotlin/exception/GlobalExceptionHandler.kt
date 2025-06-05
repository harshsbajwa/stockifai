package com.harshsbajwa.stockifai.api.exception

import com.harshsbajwa.stockifai.api.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.Instant

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        logger.warn("Validation error at ${request.requestURI}: {}", errors)

        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                message = "Validation failed",
                errors = errors,
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errors = ex.constraintViolations.map { "${it.propertyPath}: ${it.message}" }
        logger.warn("Constraint violation at ${request.requestURI}: {}", errors)

        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                message = "Invalid request parameters",
                errors = errors,
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Access denied at ${request.requestURI}: {}", ex.message)

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiResponse(
                success = false,
                message = "Access denied",
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(DataAccessException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleDataAccessException(
        ex: DataAccessException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Database error at ${request.requestURI}: {}", ex.message, ex)

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ApiResponse(
                success = false,
                message = "Service temporarily unavailable",
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Invalid argument at ${request.requestURI}: {}", ex.message)

        return ResponseEntity.badRequest().body(
            ApiResponse(
                success = false,
                message = "Invalid request: ${ex.message}",
                errors = listOf(ex.message ?: "Invalid argument"),
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(RuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleRuntimeException(
        ex: RuntimeException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errorId = System.currentTimeMillis().toString()
        logger.error("Runtime error [{}] at ${request.requestURI}: {}", errorId, ex.message, ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse(
                success = false,
                message = "Internal server error",
                errors = listOf("Error ID: $errorId"),
                timestamp = Instant.now(),
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errorId = System.currentTimeMillis().toString()
        logger.error("Unexpected error [{}] at ${request.requestURI}: {}", errorId, ex.message, ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse(
                success = false,
                message = "An unexpected error occurred",
                errors = listOf("Error ID: $errorId"),
                timestamp = Instant.now(),
            ),
        )
    }
}
