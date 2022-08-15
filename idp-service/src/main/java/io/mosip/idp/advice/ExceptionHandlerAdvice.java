/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.advice;

import io.mosip.idp.dto.ErrorDto;
import io.mosip.idp.dto.ErrorRespDto;
import io.mosip.idp.dto.ResponseWrapper;
import io.mosip.idp.exception.IdPException;
import io.mosip.idp.exception.InvalidClientException;
import io.mosip.idp.exception.NotAuthenticatedException;
import io.mosip.idp.util.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Set;

import static io.mosip.idp.util.ErrorConstants.*;

@ControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);

    @ExceptionHandler({ Exception.class, RuntimeException.class })
    public ResponseEntity handleExceptions(Exception ex, WebRequest request) {
        boolean isInternalAPI = request.getContextPath().contains("/internal");

        if(!isInternalAPI && request.getContextPath().contains("/userinfo")) {
            return handleExceptionWithHeader(ex);
        }

        if(!isInternalAPI && request.getContextPath().contains("/token")) {
            return handleOpenIdConnectControllerExceptions(ex);
        }

        return handleInternalControllerException(ex);
    }


    private ResponseEntity<ResponseWrapper> handleInternalControllerException(Exception ex) {
        if(ex instanceof MethodArgumentNotValidException) {
            FieldError fieldError = ((MethodArgumentNotValidException) ex).getBindingResult().getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage();
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(message,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }
        if(ex instanceof ConstraintViolationException) {
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            String message = !violations.isEmpty() ? violations.stream().findFirst().get().getMessage() : ex.getMessage();
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(message,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }
        if(ex instanceof InvalidClientException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(ErrorConstants.INVALID_CLIENT_ID,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }
        if(ex instanceof IdPException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(((IdPException) ex).getErrorCode(),
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }
        logger.error("Unhandled exception encountered in handler advice", ex);
        return new ResponseEntity<ResponseWrapper>(getResponseWrapper(DEFAULT_ERROR_CODE, DEFAULT_ERROR_MSG),
                HttpStatus.OK);
    }

    public ResponseEntity<ErrorRespDto> handleOpenIdConnectControllerExceptions(Exception ex) {
        if(ex instanceof MethodArgumentNotValidException) {
            FieldError fieldError = ((MethodArgumentNotValidException) ex).getBindingResult().getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage();
            return new ResponseEntity<ErrorRespDto>(getErrorRespDto(message,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof ConstraintViolationException) {
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            String message = !violations.isEmpty() ? violations.stream().findFirst().get().getMessage() : ex.getMessage();
            return new ResponseEntity<ErrorRespDto>(getErrorRespDto(message,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof NotAuthenticatedException) {
            ResponseEntity responseEntity = new ResponseEntity(HttpStatus.UNAUTHORIZED);
            HttpHeaders headers = responseEntity.getHeaders();
            headers.add("WWW-Authenticate", "error=\""+INVALID_AUTH_TOKEN+"\"");
            return responseEntity;
        }
        if(ex instanceof InvalidClientException) {
            return new ResponseEntity<ErrorRespDto>(getErrorRespDto(ErrorConstants.INVALID_CLIENT_ID,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof IdPException) {
            return new ResponseEntity<ErrorRespDto>(getErrorRespDto(((IdPException) ex).getErrorCode(),
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        logger.error("Unhandled exception encountered in handler advice", ex);
        return new ResponseEntity<ErrorRespDto>(getErrorRespDto(DEFAULT_ERROR_CODE, DEFAULT_ERROR_MSG),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity handleExceptionWithHeader(Exception ex) {
        String errorCode = DEFAULT_ERROR_CODE;
        if(ex instanceof NotAuthenticatedException) {
            errorCode = INVALID_AUTH_TOKEN;
        }
        logger.error("Unhandled exception encountered in handler advice", ex);
        ResponseEntity responseEntity = new ResponseEntity(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = responseEntity.getHeaders();
        headers.add("WWW-Authenticate", "error=\""+errorCode+"\"");
        return responseEntity;
    }

    private ErrorRespDto getErrorRespDto(String errorCode, String errorMessage) {
        ErrorRespDto errorRespDto = new ErrorRespDto();
        errorRespDto.setError(errorCode);
        errorRespDto.setError_description(errorMessage);
        return errorRespDto;
    }


    private ResponseWrapper getResponseWrapper(String errorCode, String errorMessage) {
        ResponseWrapper responseWrapper = new ResponseWrapper<>();
        responseWrapper.setErrors(new ArrayList<>());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setErrorCode(errorCode);
        errorDto.setErrorMessage(errorMessage);
        responseWrapper.getErrors().add(errorDto);
        return responseWrapper;
    }
}
