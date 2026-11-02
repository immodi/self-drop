package com.projects.selfdrop.controllers;

import com.projects.selfdrop.services.AddressService;
import com.projects.selfdrop.utils.LoopbackRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renders the settings page. The page itself only loads on the machine running
 * the server; the JSON endpoints it calls enforce the same rule.
 */
@Controller
class SettingsUiController {

    private final AddressService addressService;

    public SettingsUiController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/settings")
    public String settings(Model model, HttpServletRequest request, HttpServletResponse response) {
        boolean allowed = LoopbackRequests.isLoopback(request);

        model.addAttribute("allowed", allowed);
        model.addAttribute("address", addressService.current());
        model.addAttribute("localOnly", addressService.isLocalOnly());

        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        return "settings";
    }
}
