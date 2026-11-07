package com.projects.selfdrop.interfaces;

import com.projects.selfdrop.dtos.requests.CreateChatRequest;
import com.projects.selfdrop.dtos.requests.DeleteChatRequest;
import com.projects.selfdrop.dtos.requests.GetChatRequest;
import com.projects.selfdrop.dtos.requests.UpdateChatRequest;
import com.projects.selfdrop.dtos.responses.ChatDto;

import java.util.List;

public interface IChatService {
    List<ChatDto> getAll();
    ChatDto get(GetChatRequest request);
    ChatDto create(CreateChatRequest request);
    ChatDto update(UpdateChatRequest request);
    void delete(DeleteChatRequest request);

    /** Creates a default "General" chat when no chats exist. */
    ChatDto ensureDefault();
}
