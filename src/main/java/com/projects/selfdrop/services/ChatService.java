package com.projects.selfdrop.services;

import com.projects.selfdrop.dtos.requests.CreateChatRequest;
import com.projects.selfdrop.dtos.requests.DeleteChatRequest;
import com.projects.selfdrop.dtos.requests.DeleteFileRequest;
import com.projects.selfdrop.dtos.requests.GetChatRequest;
import com.projects.selfdrop.dtos.requests.UpdateChatRequest;
import com.projects.selfdrop.dtos.responses.ChatDto;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.entities.Chat;
import com.projects.selfdrop.exceptions.ChatNotFoundInDbException;
import com.projects.selfdrop.interfaces.IChatRepository;
import com.projects.selfdrop.interfaces.IChatService;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import com.projects.selfdrop.mappers.ChatMapper;
import com.projects.selfdrop.utils.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Primary
@Service
public class ChatService implements IChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String DEFAULT_CHAT_NAME = "General";

    private final IChatRepository chatRepository;
    private final ChatMapper chatMapper;
    private final IFileService fileService;
    private final IFileStorage fileStorage;

    public ChatService(
            IChatRepository chatRepository,
            ChatMapper chatMapper,
            IFileService fileService,
            IFileStorage fileStorage) {
        this.chatRepository = chatRepository;
        this.chatMapper = chatMapper;
        this.fileService = fileService;
        this.fileStorage = fileStorage;
    }

    public List<ChatDto> getAll() {
        return chatMapper.toDtos(chatRepository.findAll());
    }

    public ChatDto get(GetChatRequest request) {
        return chatRepository.findById(request.id())
                .map(chatMapper::toDto)
                .orElseThrow(() -> new ChatNotFoundInDbException(request.id()));
    }

    public ChatDto create(CreateChatRequest request) {
        Chat chat = new Chat(
                IdGenerator.generate(),
                request.name().trim(),
                Instant.now().getEpochSecond());
        chatRepository.create(chat);
        return chatMapper.toDto(chat);
    }

    public ChatDto update(UpdateChatRequest request) {
        Chat existing = chatRepository.findById(request.id())
                .orElseThrow(() -> new ChatNotFoundInDbException(request.id()));
        existing.setName(request.name().trim());
        if (!chatRepository.update(existing)) {
            throw new ChatNotFoundInDbException(request.id());
        }
        return chatMapper.toDto(existing);
    }

    public void delete(DeleteChatRequest request) {
        if (chatRepository.findById(request.id()).isEmpty()) {
            throw new ChatNotFoundInDbException(request.id());
        }

        // Remove the rows first (which publishes the live delete events), then
        // the stored bytes. If a byte delete fails, the orphan sweep reclaims it.
        List<FileDto> files = fileService.getAllByChat(request.id());
        for (FileDto file : files) {
            fileService.delete(new DeleteFileRequest(file.id()));
            try {
                fileStorage.delete(file.id());
            } catch (IOException e) {
                log.warn("Deleted file row {} with its chat but could not delete its bytes; "
                        + "leaving it to the orphan sweep", file.id(), e);
            }
        }

        if (!chatRepository.deleteById(request.id())) {
            throw new ChatNotFoundInDbException(request.id());
        }
    }

    public synchronized ChatDto ensureDefault() {
        List<ChatDto> chats = getAll();
        if (!chats.isEmpty()) {
            return chats.getFirst();
        }
        return create(new CreateChatRequest(DEFAULT_CHAT_NAME));
    }
}
