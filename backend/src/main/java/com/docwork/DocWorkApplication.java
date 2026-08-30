package com.docwork;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.docwork.mapper")
public class DocWorkApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocWorkApplication.class, args);
    }
}
