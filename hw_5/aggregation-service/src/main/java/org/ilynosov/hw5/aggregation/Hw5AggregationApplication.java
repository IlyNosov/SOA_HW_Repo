package org.ilynosov.hw5.aggregation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Hw5AggregationApplication {

    public static void main(String[] args) {
        SpringApplication.run(Hw5AggregationApplication.class, args);
    }
}
