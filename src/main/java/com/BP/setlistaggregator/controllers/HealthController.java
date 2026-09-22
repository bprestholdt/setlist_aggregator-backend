package com.BP.setlistaggregator.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

//lightweight endpoint for uptime pings and host health checks
//does not touch the database, so pinging it keeps the app warm without waking Postgres
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
