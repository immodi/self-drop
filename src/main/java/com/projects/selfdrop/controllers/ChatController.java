package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.CreateChatRequest;
import com.projects.selfdrop.dtos.requests.DeleteChatRequest;
import com.projects.selfdrop.dtos.requests.GetChatRequest;
import com.projects.selfdrop.dtos.requests.UpdateChatRequest;
import com.projects.selfdrop.dtos.responses.ChatDto;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.interfaces.IChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/chat")
class ChatController {
    private final IChatService chatService;

    public ChatController(IChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<List<ChatDto>> getAll() {
        return ResponseEntity.ok(chatService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatDto> get(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(chatService.get(new GetChatRequest(id)));
    }

    @PostMapping
    public ResponseEntity<ChatDto> create(@Valid @RequestBody CreateChatRequest request) {
        ChatDto created = chatService.create(request);
        return ResponseEntity
                .created(URI.create("/chat/" + created.id()))
                .body(created);
    }

    @PutMapping
    public ResponseEntity<ChatDto> update(@Valid @RequestBody UpdateChatRequest request) {
        return ResponseEntity.ok(chatService.update(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank String id) {
        chatService.delete(new DeleteChatRequest(id));
        return ResponseEntity.noContent().build();
    }
}
