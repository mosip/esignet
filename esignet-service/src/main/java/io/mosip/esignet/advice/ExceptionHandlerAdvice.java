/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.advice;

import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.OAuthError;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.dto.vci.VCError;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidClientException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
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
import org.springframework.util.MultiValueMap;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.*;

import static io.mosip.esignet.core.constants.ErrorConstants.*;

@Slf4j
@ControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler implements AccessDeniedHandler {

    @Autowired
    MessageSource messageSource;


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

    @ExceptionHandler(value = { Exception.class, RuntimeException.class, MissingRequestHeaderException.class })
    public ResponseEntity handleExceptions(Exception ex, WebRequest request) {
        log.error("Unhandled exception encountered in handler advice", ex);
        String pathInfo = ((ServletWebRequest)request).getRequest().getPathInfo();

        boolean isInternalAPI = pathInfo != null && (pathInfo.startsWith("/authorization") ||
                pathInfo.startsWith("/client-mgmt/"));

        if(!isInternalAPI && pathInfo.startsWith("/oidc/userinfo")) {
            return handleExceptionWithHeader(ex);
        }

        if(!isInternalAPI && pathInfo.startsWith("/oauth/")) {
            return handleOpenIdConnectControllerExceptions(ex);
        }

        if(!isInternalAPI && pathInfo.startsWith("/vci/")) {
            return handleVCIControllerExceptions(ex);
        }

        return handleInternalControllerException(ex);
    }


    private ResponseEntity<ResponseWrapper> handleInternalControllerException(Exception ex) {
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
        if(ex instanceof MissingServletRequestParameterException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(INVALID_REQUEST, ex.getMessage()),
                    HttpStatus.OK);
        }
        if(ex instanceof HttpMediaTypeNotAcceptableException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(INVALID_REQUEST, ex.getMessage()),
                    HttpStatus.OK);
        }
        if(ex instanceof InvalidClientException) {
            return new ResponseEntity<ResponseWrapper>(getResponseWrapper(INVALID_CLIENT_ID, getMessage(INVALID_CLIENT_ID)), HttpStatus.OK);
        }
        if(ex instanceof EsignetException) {
            String errorCode = ((EsignetException) ex).getErrorCode();
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

    public ResponseEntity<OAuthError> handleOpenIdConnectControllerExceptions(Exception ex) {
        if(ex instanceof MethodArgumentNotValidException) {
            FieldError fieldError = ((MethodArgumentNotValidException) ex).getBindingResult().getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage();
            return new ResponseEntity<OAuthError>(getErrorRespDto(INVALID_INPUT, message), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof ConstraintViolationException) {
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            String message = !violations.isEmpty() ? violations.stream().findFirst().get().getMessage() : ex.getMessage();
            return new ResponseEntity<OAuthError>(getErrorRespDto(INVALID_INPUT, message), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof InvalidRequestException) {
            String errorCode = ((EsignetException) ex).getErrorCode();
            return new ResponseEntity<OAuthError>(getErrorRespDto(errorCode, getMessage(errorCode)), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof EsignetException) {
            String errorCode = ((EsignetException) ex).getErrorCode();
            return new ResponseEntity<OAuthError>(getErrorRespDto(errorCode, getMessage(errorCode)), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        log.error("Unhandled exception encountered in handler advice", ex);
        return new ResponseEntity<OAuthError>(getErrorRespDto(UNKNOWN_ERROR, ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ResponseEntity<VCError> handleVCIControllerExceptions(Exception ex) {
        if(ex instanceof MethodArgumentNotValidException) {
            FieldError fieldError = ((MethodArgumentNotValidException) ex).getBindingResult().getFieldError();
            String message = fieldError != null ? fieldError.getDefaultMessage() : ex.getMessage();
            return new ResponseEntity<VCError>(getVCErrorDto(message, message), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof ConstraintViolationException) {
            Set<ConstraintViolation<?>> violations = ((ConstraintViolationException) ex).getConstraintViolations();
            String message = !violations.isEmpty() ? violations.stream().findFirst().get().getMessage() : ex.getMessage();
            return new ResponseEntity<VCError>(getVCErrorDto(message, message), HttpStatus.BAD_REQUEST);
        }
        if(ex instanceof NotAuthenticatedException) {
            String errorCode = ((EsignetException) ex).getErrorCode();
            return new ResponseEntity<VCError>(getVCErrorDto(errorCode, getMessage(errorCode)), HttpStatus.UNAUTHORIZED);
        }
        if(ex instanceof InvalidRequestException | ex instanceof EsignetException) {
            String errorCode = ((EsignetException) ex).getErrorCode();
            return new ResponseEntity<VCError>(getVCErrorDto(errorCode, getMessage(errorCode)), HttpStatus.BAD_REQUEST);
        }
        log.error("Unhandled exception encountered in handler advice", ex);
        return new ResponseEntity<VCError>(getVCErrorDto(UNKNOWN_ERROR, ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity handleExceptionWithHeader(Exception ex) {
        String errorCode = UNKNOWN_ERROR;
        if(ex instanceof NotAuthenticatedException) {
            errorCode = INVALID_AUTH_TOKEN;
        }
        if(ex instanceof MissingRequestHeaderException) {
            errorCode = MISSING_HEADER;
        }
        log.error("Unhandled exception encountered in handler advice", ex);
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add("WWW-Authenticate", "error=\""+errorCode+"\"");
        ResponseEntity responseEntity = new ResponseEntity(headers, HttpStatus.UNAUTHORIZED);
        return responseEntity;
    }

    private OAuthError getErrorRespDto(String errorCode, String errorMessage) {
        OAuthError errorRespDto = new OAuthError();
        errorRespDto.setError(errorCode);
        errorRespDto.setError_description(errorMessage);
        return errorRespDto;
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

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        handleExceptions(accessDeniedException, (WebRequest) request);
    }

    private String getMessage(String errorCode) {
        try {
            messageSource.getMessage(errorCode, null, errorCode, Locale.getDefault());
        } catch (NoSuchMessageException ex) {
            log.error("Message not found in the i18n bundle", ex);
        }
        return errorCode;
    }

    private VCError getVCErrorDto(String errorCode, String description) {
        VCError errorRespDto = new VCError();
        errorRespDto.setError(errorCode);
        errorRespDto.setError_description(description);
        return errorRespDto;
    }
}
