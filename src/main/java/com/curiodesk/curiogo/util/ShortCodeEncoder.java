package com.curiodesk.curiogo.util;

public interface ShortCodeEncoder {
    String encode(long id);

    long decode(String code);
}
