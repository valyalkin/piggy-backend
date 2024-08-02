package com.valyalkin.piggy.configuration

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
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
}
