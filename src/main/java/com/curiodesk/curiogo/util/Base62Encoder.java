package com.curiodesk.curiogo.util;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.encoder", havingValue = "base62", matchIfMissing = true)
public class Base62Encoder implements ShortCodeEncoder {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    @Override
    public String encode(long id) {
        if (id == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        // We built the digits least-significant first, so flip to most-significant first.
        return sb.reverse().toString();
    }

    @Override
    public long decode(String code) {
        long id = 0;
        for (char c : code.toCharArray()) {
            id = id * BASE + ALPHABET.indexOf(c);
        }
        return id;
    }
}
