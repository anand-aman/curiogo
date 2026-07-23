package com.curiodesk.curiogo.exception;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Rate limit exceeded. Too many links created - please retry shortly.");
    }
}
