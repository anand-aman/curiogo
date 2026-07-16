package com.curiodesk.curiogo.exception;

import com.curiodesk.curiogo.util.ReservedAliases;

public class ReservedAliasException extends RuntimeException{

    public ReservedAliasException(String alias) {
        super("Alias '" + alias + "' is reserved and cannot be used..");
    }
}
