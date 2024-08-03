package com.valyalkin.piggy.configuration

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        BusinessException::class,
    )
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handle400(
        request: HttpServletRequest,
        e: Throwable,
    ): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            e.message ?: "Bad request",
        ).also {
            log.debug("Bad Request: ${e.message}")
        }
    }

    @ExceptionHandler(
        SystemException::class,
    )
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handle500(
        request: HttpServletRequest,
        e: Throwable,
    ): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            e.message ?: "Internal server error",
        ).also {
            log.error("Internal Server Error: ${e.message}")
        }
    }

    @ExceptionHandler(
        NotFoundException::class,
    )
    @ResponseBody
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handle404(
        request: HttpServletRequest,
        e: Throwable,
    ): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            e.message ?: "Not found",
        ).also {
            log.debug("Not found ${e.message}")
        }
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
    )
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationErrors(
        request: HttpServletRequest,
        e: MethodArgumentNotValidException,
    ): ProblemDetail {
        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            properties =
                e.bindingResult.fieldErrors.associate {
                    it.field to it.defaultMessage
                }
        }
    }
}
