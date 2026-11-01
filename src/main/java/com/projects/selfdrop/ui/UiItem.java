package com.projects.selfdrop.ui;

import com.projects.selfdrop.dtos.responses.FileDto;

/**
 * A timeline item with the values the template needs already formatted.
 *
 * <p>Doing this in Java keeps bean and static access out of {@code th:utext},
 * which Thymeleaf evaluates in restricted mode.</p>
 */
public record UiItem(
        Long seq,
        String id,
        String kind,
        String text,
        String textHtml,
        String name,
        Long size,
        Long createdAt,
        String time,
        String iso,
        String sizeLabel,
        boolean image
) {

    public static UiItem from(FileDto item) {
        return new UiItem(
                item.seq(),
                item.id(),
                item.kind(),
                item.text(),
                UiFormat.linkify(item.text()),
                item.name(),
                item.size(),
                item.createdAt(),
                UiFormat.time(item.createdAt()),
                UiFormat.iso(item.createdAt()),
                UiFormat.size(item.size()),
                UiFormat.isPreviewable(item)
        );
    }
}
