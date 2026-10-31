package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.responses.ServerIdDto;
import com.projects.selfdrop.services.ServerIdService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/ping")
class HealthController {
    private final ServerIdService serverIdService;

    public HealthController(ServerIdService serverIdService)
    {
        this.serverIdService = serverIdService;
    }

    @GetMapping
    public ServerIdDto ping() {
        return new ServerIdDto(serverIdService.value());
    }
}
