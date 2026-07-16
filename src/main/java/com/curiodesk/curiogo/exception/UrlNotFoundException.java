package com.curiodesk.curiogo.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String code) {
        super("No URL found for code '" + code + "'.");
    }
}
