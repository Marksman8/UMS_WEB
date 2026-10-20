package edu.ums.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point. Feature packages hang off this root: academic, student, faculty, admin, dev, ai, notice. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(UmsApplication.class, args);
    }
}
