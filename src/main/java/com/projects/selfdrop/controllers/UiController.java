package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.CreateChatRequest;
import com.projects.selfdrop.dtos.requests.DeleteChatRequest;
import com.projects.selfdrop.dtos.requests.UpdateChatRequest;
import com.projects.selfdrop.dtos.requests.UploadTextRequest;
import com.projects.selfdrop.dtos.responses.ChatDto;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.interfaces.IChatService;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.services.AddressService;
import com.projects.selfdrop.ui.UiItem;
import com.projects.selfdrop.utils.IdGenerator;
import com.projects.selfdrop.utils.LoopbackRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serves the browser UI. The page itself is rendered here; htmx then talks to
 * this controller for the timeline fragment and new text items, and to the
 * existing JSON API for file uploads and deletes.
 */
@Controller
class UiController {

    private final IChatService chatService;
    private final IFileService fileService;
    private final AddressService addressService;

    public UiController(
            IChatService chatService,
            IFileService fileService,
            AddressService addressService) {
        this.chatService = chatService;
        this.fileService = fileService;
        this.addressService = addressService;
    }

    @GetMapping("/")
    public String index(
            @RequestParam(required = false) String chat,
            Model model,
            HttpServletRequest request) {
        List<ChatDto> chats = chatService.getAll();
        if (chats.isEmpty()) {
            chatService.ensureDefault();
            chats = chatService.getAll();
        }

        ChatDto selected = selectChat(chats, chat);
        List<FileDto> files = newestFirst(fileService.getAllByChat(selected.id()));

        model.addAttribute("chats", chats);
        model.addAttribute("selectedChat", selected);
        model.addAttribute("chatId", selected.id());
        model.addAttribute("items", files.stream().map(UiItem::from).toList());
        model.addAttribute("since", fileService.maxSeq());
        model.addAttribute("lanUrl", addressService.isLocalOnly() ? null : addressService.current().url());
        model.addAttribute("canManageSettings", LoopbackRequests.isLoopback(request));
        return "index";
    }

    @GetMapping("/ui/timeline")
    public String timeline(@RequestParam String chatId, Model model) {
        model.addAttribute("items", newestFirst(fileService.getAllByChat(chatId)).stream()
                .map(UiItem::from)
                .toList());
        return "fragments/timeline :: items";
    }

    @PostMapping("/ui/text")
    public String createText(
            @RequestParam @NotBlank String chatId,
            @RequestParam @NotBlank String text,
            Model model) {
        FileDto created = fileService.create(
                new UploadTextRequest(text).toCreateFileRequest(IdGenerator.generate(), chatId));
        model.addAttribute("item", UiItem.from(created));
        return "fragments/item :: item";
    }

    @PostMapping("/ui/chat")
    public String createChat(@RequestParam(required = false) String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "redirect:/";
        }
        ChatDto created = chatService.create(new CreateChatRequest(trimmed));
        return "redirect:/?chat=" + created.id();
    }

    @PostMapping("/ui/chat/{id}/rename")
    public String renameChat(
            @PathVariable String id,
            @RequestParam(required = false) String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            return "redirect:/?chat=" + id;
        }
        chatService.update(new UpdateChatRequest(id, trimmed));
        return "redirect:/?chat=" + id;
    }

    @PostMapping("/ui/chat/{id}/delete")
    public String deleteChat(@PathVariable String id) {
        chatService.delete(new DeleteChatRequest(id));
        return "redirect:/";
    }

    private static ChatDto selectChat(List<ChatDto> chats, String requestedId) {
        if (requestedId != null) {
            for (ChatDto chat : chats) {
                if (chat.id().equals(requestedId)) {
                    return chat;
                }
            }
        }
        return chats.getFirst();
    }

    private static List<FileDto> newestFirst(List<FileDto> items) {
        List<FileDto> ordered = new ArrayList<>(items);
        Collections.reverse(ordered);
        return ordered;
    }
}
