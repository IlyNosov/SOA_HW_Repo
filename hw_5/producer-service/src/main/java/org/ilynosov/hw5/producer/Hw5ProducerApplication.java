package org.ilynosov.hw5.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Hw5ProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(Hw5ProducerApplication.class, args);
    }
}
