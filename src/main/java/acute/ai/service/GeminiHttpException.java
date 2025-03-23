package acute.ai.service;

/**
 * Exception for Gemini API errors
 */
public class GeminiHttpException extends RuntimeException {
    public final int statusCode;
    public final String type;
    
    public GeminiHttpException(String message, int statusCode, String type) {
        super(message);
        this.statusCode = statusCode;
        this.type = type;
    }
    
    public GeminiHttpException(String message, int statusCode, String type, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.type = type;
    }
}