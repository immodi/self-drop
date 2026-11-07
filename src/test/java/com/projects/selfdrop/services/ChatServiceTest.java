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
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import com.projects.selfdrop.mappers.ChatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final long CREATED_AT = 1_700_000_000L;

    @Mock
    private IChatRepository chatRepository;

    @Mock
    private IFileService fileService;

    @Mock
    private IFileStorage fileStorage;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRepository, new ChatMapper(), fileService, fileStorage);
    }

    private static Chat entity(String id, String name) {
        return new Chat(id, name, CREATED_AT);
    }

    private static ChatDto dto(Chat chat) {
        return new ChatDto(
                chat.getSeq(),
                chat.getId(),
                chat.getName(),
                chat.getCreatedAt());
    }

    @Test
    void getAllMapsEveryRowInOrder() {
        when(chatRepository.findAll()).thenReturn(List.of(entity("a", "A"), entity("b", "B")));

        assertThat(chatService.getAll())
                .containsExactly(dto(entity("a", "A")), dto(entity("b", "B")));
    }

    @Test
    void getReturnsMappedDto() {
        Chat chat = entity("id1", "General");
        when(chatRepository.findById("id1")).thenReturn(Optional.of(chat));

        assertThat(chatService.get(new GetChatRequest("id1"))).isEqualTo(dto(chat));
    }

    @Test
    void getThrowsWhenChatIsMissing() {
        when(chatRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.get(new GetChatRequest("missing")))
                .isInstanceOf(ChatNotFoundInDbException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void createTrimsTheNameAndPersistsTheChat() {
        ChatDto created = chatService.create(new CreateChatRequest("  Work  "));

        ArgumentCaptor<Chat> captor = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).create(captor.capture());
        Chat saved = captor.getValue();

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getName()).isEqualTo("Work");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(created).isEqualTo(dto(saved));
    }

    @Test
    void updateChangesTheName() {
        Chat existing = entity("id1", "Old");
        when(chatRepository.findById("id1")).thenReturn(Optional.of(existing));
        when(chatRepository.update(any(Chat.class))).thenReturn(true);

        ChatDto updated = chatService.update(new UpdateChatRequest("id1", " New "));

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void updateThrowsWhenChatIsMissing() {
        when(chatRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.update(new UpdateChatRequest("missing", "New")))
                .isInstanceOf(ChatNotFoundInDbException.class);

        verify(chatRepository, never()).update(any());
    }

    @Test
    void deleteRemovesFilesThenTheChat() throws IOException {
        when(chatRepository.findById("id1")).thenReturn(Optional.of(entity("id1", "General")));
        when(fileService.getAllByChat("id1")).thenReturn(List.of(
                new FileDto(1L, "file1", "id1", "text", "hello", null, null, 5L, CREATED_AT),
                new FileDto(2L, "file2", "id1", "file", null, "a.txt", "text/plain", 3L, CREATED_AT)));
        when(chatRepository.deleteById("id1")).thenReturn(true);

        assertThatCode(() -> chatService.delete(new DeleteChatRequest("id1")))
                .doesNotThrowAnyException();

        verify(fileService).delete(new DeleteFileRequest("file1"));
        verify(fileService).delete(new DeleteFileRequest("file2"));
        verify(fileStorage).delete("file1");
        verify(fileStorage).delete("file2");
        verify(chatRepository).deleteById("id1");
    }

    @Test
    void deleteKeepsGoingWhenBytesCannotBeRemoved() throws IOException {
        when(chatRepository.findById("id1")).thenReturn(Optional.of(entity("id1", "General")));
        when(fileService.getAllByChat("id1")).thenReturn(List.of(
                new FileDto(1L, "file1", "id1", "file", null, "a.txt", "text/plain", 3L, CREATED_AT)));
        doThrow(new IOException("disk on fire")).when(fileStorage).delete("file1");
        when(chatRepository.deleteById("id1")).thenReturn(true);

        assertThatCode(() -> chatService.delete(new DeleteChatRequest("id1")))
                .doesNotThrowAnyException();

        verify(chatRepository).deleteById("id1");
    }

    @Test
    void deleteThrowsWhenChatIsMissing() {
        when(chatRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.delete(new DeleteChatRequest("missing")))
                .isInstanceOf(ChatNotFoundInDbException.class);

        verify(fileService, never()).getAllByChat(any());
        verify(chatRepository, never()).deleteById(any());
    }

    @Test
    void ensureDefaultReturnsTheExistingFirstChat() {
        Chat existing = entity("id1", "General");
        when(chatRepository.findAll()).thenReturn(List.of(existing));

        assertThat(chatService.ensureDefault()).isEqualTo(dto(existing));
        verify(chatRepository, never()).create(any());
    }

    @Test
    void ensureDefaultCreatesGeneralWhenThereAreNoChats() {
        when(chatRepository.findAll()).thenReturn(List.of());

        ChatDto created = chatService.ensureDefault();

        assertThat(created.name()).isEqualTo("General");
        verify(chatRepository).create(any(Chat.class));
    }
}
