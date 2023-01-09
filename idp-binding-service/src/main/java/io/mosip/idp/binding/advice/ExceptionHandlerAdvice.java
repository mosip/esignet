/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.advice;

import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.dto.ResponseWrapper;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.*;

import static io.mosip.idp.core.util.ErrorConstants.*;

@Slf4j
@ControllerAdvice
public class ExceptionHandlerAdvice  extends ResponseEntityExceptionHandler implements AccessDeniedHandler {

    @Autowired
    private MessageSource messageSource;

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatus status, WebRequest request) {
        return handleExceptions(ex, request);
    }

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

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        handleExceptions(accessDeniedException, (WebRequest) request);
    }

    @ExceptionHandler(value = { Exception.class, RuntimeException.class, MissingRequestHeaderException.class })
    public ResponseEntity handleExceptions(Exception ex, WebRequest request) {
        log.error("Unhandled exception encountered in handler advice", ex);
        if(ex instanceof MethodArgumentNotValidException) {
            List<Error> errors = new ArrayList<>();
            for (FieldError error : ((MethodArgumentNotValidException) ex).getBindingResult().getFieldErrors()) {
                errors.add(new Error(error.getDefaultMessage(), error.getField() + ": " + error.getDefaultMessage()));
            }
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(errors), HttpStatus.OK);
        }
        if(ex instanceof ConstraintViolationException) {
            List<Error> errors = new ArrayList<>();
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            for(ConstraintViolation<?> cv : violations) {
                errors.add(new Error(INVALID_REQUEST,cv.getPropertyPath().toString() + ": " + cv.getMessage()));
            }
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(errors), HttpStatus.OK);
        }
        if(ex instanceof MissingServletRequestParameterException || ex instanceof HttpMediaTypeNotAcceptableException || ex instanceof HttpMessageNotReadableException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(INVALID_REQUEST, ex.getMessage()),
                    HttpStatus.OK);
        }
        if(ex instanceof IdPException) {
            String errorCode = ((IdPException) ex).getErrorCode();
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(errorCode, getMessage(errorCode)), HttpStatus.OK);
        }
        if(ex instanceof AuthenticationCredentialsNotFoundException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(HttpStatus.UNAUTHORIZED.name(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase()), HttpStatus.UNAUTHORIZED);
        }
        if(ex instanceof AccessDeniedException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(HttpStatus.FORBIDDEN.name(),
                    HttpStatus.FORBIDDEN.getReasonPhrase()), HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<ResponseWrapper>(getResponseWrapper(UNKNOWN_ERROR, ex.getMessage()), HttpStatus.OK);
    }

    private ResponseWrapper getResponseWrapper(String errorCode, String errorMessage) {
        Error error = new Error();
        error.setErrorCode(errorCode);
        error.setErrorMessage(errorMessage);
        return getResponseWrapper(Arrays.asList(error));
    }

    private ResponseWrapper getResponseWrapper(List<Error> errors) {
        ResponseWrapper responseWrapper = new ResponseWrapper<>();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setErrors(errors);
        return responseWrapper;
    }

    private String getMessage(String errorCode) {
        try {
            messageSource.getMessage(errorCode, null, Locale.getDefault());
        } catch (NoSuchMessageException ex) {
            log.error("Message not found in the i18n bundle", ex);
        }
        return errorCode;
    }
}
