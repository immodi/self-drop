package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.UploadTextRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.ui.UiItem;
import com.projects.selfdrop.utils.IdGenerator;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Serves the browser UI. The page itself is rendered here; htmx then talks to
 * this controller for the timeline fragment and new text items, and to the
 * existing JSON API for file uploads and deletes.
 */
@Controller
class UiController {

    private final IFileService fileService;

    public UiController(IFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<FileDto> files = newestFirst(fileService.getAll());
        model.addAttribute("items", files.stream().map(UiItem::from).toList());
        model.addAttribute("since", files.stream()
                .map(FileDto::seq)
                .filter(Objects::nonNull)
                .max(Long::compare)
                .orElse(0L));
        return "index";
    }

    @GetMapping("/ui/timeline")
    public String timeline(Model model) {
        model.addAttribute("items", newestFirst(fileService.getAll()).stream()
                .map(UiItem::from)
                .toList());
        return "fragments/timeline :: items";
    }

    @PostMapping("/ui/text")
    public String createText(@RequestParam @NotBlank String text, Model model) {
        FileDto created = fileService.create(
                new UploadTextRequest(text).toCreateFileRequest(IdGenerator.generate()));
        model.addAttribute("item", UiItem.from(created));
        return "fragments/item :: item";
    }

    private static List<FileDto> newestFirst(List<FileDto> items) {
        List<FileDto> ordered = new ArrayList<>(items);
        Collections.reverse(ordered);
        return ordered;
    }
}
