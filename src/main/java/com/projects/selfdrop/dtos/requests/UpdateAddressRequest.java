package com.projects.selfdrop.dtos.requests;

import jakarta.validation.constraints.NotBlank;

/**
 * A manual override for the address this server advertises to clients.
 */
public record UpdateAddressRequest(@NotBlank String url) {
}
