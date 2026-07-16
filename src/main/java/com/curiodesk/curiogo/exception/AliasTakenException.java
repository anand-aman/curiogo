package com.curiodesk.curiogo.exception;

public class AliasTakenException extends RuntimeException {

    public AliasTakenException(String alias) {
        super("Alias '" + alias + "' is already taken.");
    }

}
