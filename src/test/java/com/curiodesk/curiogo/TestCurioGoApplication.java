package com.curiodesk.curiogo;

import org.springframework.boot.SpringApplication;

public class TestCurioGoApplication {
    public static void main(String[] args) {
        SpringApplication.from(CurioGoApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
