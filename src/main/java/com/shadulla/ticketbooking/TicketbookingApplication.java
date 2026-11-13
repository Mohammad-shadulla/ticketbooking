package com.shadulla.ticketbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.shadulla.ticketbooking.repository")
public class TicketbookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketbookingApplication.class, args);
    }
}