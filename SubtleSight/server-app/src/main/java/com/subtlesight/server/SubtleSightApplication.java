package com.subtlesight.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.subtlesight")
@EnableScheduling
public class SubtleSightApplication {
    public static void main(String[] args) { SpringApplication.run(SubtleSightApplication.class,args); }
}

