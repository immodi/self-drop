package com.projects.selfdrop.dtos.responses;

/**
 * The address this server currently advertises, plus whether it only works on
 * the server itself.
 */
public record AddressDto(String url, String serverId, boolean localOnly) {
}
