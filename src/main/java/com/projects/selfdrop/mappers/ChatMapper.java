package com.projects.selfdrop.mappers;

import com.projects.selfdrop.dtos.responses.ChatDto;
import com.projects.selfdrop.entities.Chat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatMapper {

    public ChatDto toDto(Chat chat) {
        return new ChatDto(
                chat.getSeq(),
                chat.getId(),
                chat.getName(),
                chat.getCreatedAt()
        );
    }

    public List<ChatDto> toDtos(List<Chat> chats) {
        return chats.stream().map(this::toDto).toList();
    }
}
