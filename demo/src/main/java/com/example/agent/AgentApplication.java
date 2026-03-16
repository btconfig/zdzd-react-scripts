package com.example.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the planning agent backend. This application exposes
 * REST endpoints for generating DSL plans from user questions and
 * executing sample tooling. It also configures an in-memory database
 * loaded with sample customer support conversations.
 */
@SpringBootApplication
@MapperScan("com.example.agent.infrastructure.mapper")
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
