/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.advice;

import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.dto.OauthError;
import io.mosip.idp.core.dto.ResponseWrapper;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.util.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.mosip.idp.core.util.ErrorConstants.*;

@ControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);

    @Override

    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return handleExceptions(ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        return handleExceptions(ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        return handleExceptions(ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatus status, WebRequest request) {
        return handleExceptions(ex, request);
    }

    @ExceptionHandler(value = { Exception.class, RuntimeException.class })
    public ResponseEntity handleExceptions(Exception ex, WebRequest request) {
        logger.error("Unhandled exception encountered in handler advice", ex);

        boolean isInternalAPI = request.getContextPath().contains("/authorization") ||
                request.getContextPath().contains("/client-mgmt");

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
            List<Error> errors = new ArrayList<>();
            for (FieldError error : ((MethodArgumentNotValidException) ex).getBindingResult().getFieldErrors()) {
                errors.add(new Error(error.getDefaultMessage(), error.getField() + ": " + error.getDefaultMessage()));
            }
            for (ObjectError error : ((MethodArgumentNotValidException) ex).getBindingResult().getGlobalErrors()) {
                errors.add(new Error(error.getDefaultMessage(), error.getObjectName()+ ": " + error.getDefaultMessage()));
            }
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(errors), HttpStatus.OK);
        }
        if(ex instanceof ConstraintViolationException) {
            List<Error> errors = new ArrayList<>();
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            for(ConstraintViolation<?> cv : violations) {
                errors.add(new Error(BAD_REQ_ERROR_CODE,cv.getPropertyPath().toString() + ": " + cv.getMessage()));
            }
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(errors), HttpStatus.OK);
        }
        if(ex instanceof MissingServletRequestParameterException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(BAD_REQ_ERROR_CODE, ex.getMessage()),
                    HttpStatus.OK);
        }
        if(ex instanceof HttpMediaTypeNotAcceptableException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(BAD_REQ_ERROR_CODE, ex.getMessage()),
                    HttpStatus.OK);
        }
        if(ex instanceof InvalidClientException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(ErrorConstants.INVALID_CLIENT_ID,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }
        if(ex instanceof IdPException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(((IdPException) ex).getErrorCode(),
                    INVALID_INPUT_ERROR_MSG), HttpStatus.OK);
        }
        return new ResponseEntity<ResponseWrapper>(getResponseWrapper(DEFAULT_ERROR_CODE, DEFAULT_ERROR_MSG),
                HttpStatus.OK);
    }

    public ResponseEntity<OauthError> handleOpenIdConnectControllerExceptions(Exception ex) {
        if(ex instanceof MethodArgumentNotValidException) {
            FieldError fieldError = ((MethodArgumentNotValidException) ex).getBindingResult().getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage();
            return new ResponseEntity<OauthError>(getErrorRespDto(message,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof ConstraintViolationException) {
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            String message = !violations.isEmpty() ? violations.stream().findFirst().get().getMessage() : ex.getMessage();
            return new ResponseEntity<OauthError>(getErrorRespDto(message,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof NotAuthenticatedException) {
            ResponseEntity responseEntity = new ResponseEntity(HttpStatus.UNAUTHORIZED);
            HttpHeaders headers = responseEntity.getHeaders();
            headers.add("WWW-Authenticate", "error=\""+INVALID_AUTH_TOKEN+"\"");
            return responseEntity;
        }
        if(ex instanceof InvalidClientException) {
            return new ResponseEntity<OauthError>(getErrorRespDto(ErrorConstants.INVALID_CLIENT_ID,
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof IdPException) {
            return new ResponseEntity<OauthError>(getErrorRespDto(((IdPException) ex).getErrorCode(),
                    INVALID_INPUT_ERROR_MSG), HttpStatus.BAD_REQUEST);
        }
        logger.error("Unhandled exception encountered in handler advice", ex);
        return new ResponseEntity<OauthError>(getErrorRespDto(DEFAULT_ERROR_CODE, DEFAULT_ERROR_MSG),
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

    private OauthError getErrorRespDto(String errorCode, String errorMessage) {
        OauthError errorRespDto = new OauthError();
        errorRespDto.setError(errorCode);
        errorRespDto.setError_description(errorMessage);
        return errorRespDto;
    }


    private ResponseWrapper getResponseWrapper(String errorCode, String errorMessage) {
        ResponseWrapper responseWrapper = new ResponseWrapper<>();
        responseWrapper.setErrors(new ArrayList<>());
        Error error = new Error();
        error.setErrorCode(errorCode);
        error.setErrorMessage(errorMessage);
        responseWrapper.getErrors().add(error);
        return responseWrapper;
    }

    private ResponseWrapper getResponseWrapper(List<Error> errors) {
        ResponseWrapper responseWrapper = new ResponseWrapper<>();
        responseWrapper.setErrors(errors);
        return responseWrapper;
    }
}
