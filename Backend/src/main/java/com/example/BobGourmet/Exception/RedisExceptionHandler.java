package com.example.BobGourmet.Exception;

import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class RedisExceptionHandler {

  @ExceptionHandler({DataAccessException.class, RedisConnectionFailureException.class})
  public ResponseEntity<Map<String,String>> handleRedisFailure(Exception e){

    log.error("Redis operation failed", e);

    Map<String,String> error = new HashMap<>();
    error.put("error", "Service temporarily unavailable");
    error.put("message", "Please try again in a moment.");
    return ResponseEntity.status(503).body(error);
  }
}
