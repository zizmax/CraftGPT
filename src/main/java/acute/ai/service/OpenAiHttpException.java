package acute.ai.service;

/**
 * Exception for OpenAI API errors
 */
public class OpenAiHttpException extends RuntimeException {
    public final int statusCode;
    public final String type;
    
    public OpenAiHttpException(String message, int statusCode, String type) {
        super(message);
        this.statusCode = statusCode;
        this.type = type;
    }
    
    public OpenAiHttpException(String message, int statusCode, String type, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.type = type;
    }
}