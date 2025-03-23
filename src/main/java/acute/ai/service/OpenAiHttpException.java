package acute.ai.service;

/**
 * Replacement for Theokanning OpenAiHttpException
 */
public class OpenAiHttpException extends RuntimeException {
    public final int statusCode;
    public final String type;

    public OpenAiHttpException(String message, int statusCode, String type) {
        super(message);
        this.statusCode = statusCode;
        this.type = type;
    }

    public OpenAiHttpException(String message, Throwable cause, int statusCode, String type) {
        super(message, cause);
        this.statusCode = statusCode;
        this.type = type;
    }
}