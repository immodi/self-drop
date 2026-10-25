package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.IdDto;
import com.projects.selfdrop.repositories.FileRepository;
import com.projects.selfdrop.utils.IdGenerator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/ping")
class PingController {
    private final IdGenerator idGenerator;

    public PingController(IdGenerator idGenerator)
    {
        this.idGenerator = idGenerator;
    }

    @GetMapping
    public IdDto ping() {
        return new IdDto(idGenerator.getServerId());
    }
}
