package com.subtlesight.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class) ProblemDetail validation(MethodArgumentNotValidException e){ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"request validation failed");p.setProperty("errors",e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).toList());return p;}
    @ExceptionHandler(IllegalArgumentException.class) ProblemDetail bad(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage()==null?"invalid request":e.getMessage());}
    @ExceptionHandler(SecurityException.class) ProblemDetail forbidden(SecurityException e){return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,e.getMessage());}
    @ExceptionHandler(NoSuchElementException.class) ProblemDetail missing(NoSuchElementException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"resource not found");}
    @ExceptionHandler(IllegalStateException.class) ProblemDetail conflict(IllegalStateException e){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage()==null?"operation rejected":e.getMessage());}
}
