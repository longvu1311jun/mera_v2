package mera.mera_v2.pos.sync.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class ApiClientException extends RuntimeException {

  private final HttpStatusCode statusCode;
  private final String responseBody;

  public ApiClientException(String message) {
    super(message);
    this.statusCode = null;
    this.responseBody = null;
  }

  public ApiClientException(String message, Throwable cause) {
    super(message, cause);
    this.statusCode = null;
    this.responseBody = null;
  }

  public ApiClientException(String message, HttpStatusCode statusCode, String responseBody) {
    super(message);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public ApiClientException(String message, Throwable cause, HttpStatusCode statusCode, String responseBody) {
    super(message, cause);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }
}