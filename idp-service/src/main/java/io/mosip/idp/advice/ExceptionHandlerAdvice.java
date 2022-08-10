package io.mosip.idp.advice;

import io.mosip.idp.dto.ErrorDto;
import io.mosip.idp.dto.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;

@ControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);
    private static final String DEFAULT_ERROR_CODE = "MOS-IDP-500";
    private static final String DEFAULT_ERROR_MSG = "UNKNOWN ERROR";
    private static final String INVALID_INPUT_ERROR_MSG = "UNSUPPORTED INPUT PARAMETER";

    @ExceptionHandler({ Exception.class, RuntimeException.class })
    public ResponseEntity<ResponseWrapper> handleExceptions(Exception ex, WebRequest request) {

        if(ex instanceof MethodArgumentNotValidException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(ex.getMessage(),
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }

        logger.error("Unhandled exception encountered in handler advice", ex);
        return new ResponseEntity<ResponseWrapper>(getResponseWrapper(DEFAULT_ERROR_CODE, DEFAULT_ERROR_MSG),
                HttpStatus.OK);
    }

    private ResponseWrapper getResponseWrapper(String errorCode, String errorMessage) {
        ResponseWrapper responseWrapper = new ResponseWrapper<>();
        responseWrapper.setErrorDtos(new ArrayList<>());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setErrorCode(errorCode);
        errorDto.setErrorMessage(errorMessage);
        responseWrapper.getErrorDtos().add(errorDto);
        return responseWrapper;
    }
}
