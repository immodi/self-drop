package com.projects.selfdrop.controllers;

import com.projects.selfdrop.services.AddressService;
import com.projects.selfdrop.services.QRService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

/**
 * Serves the pairing page: two QR codes (the pair deep link and the plain
 * server address) plus copyable text for the address this server currently
 * advertises.
 *
 * <p>The codes poll themselves so a change to the stored address (for example
 * from the future settings page) shows up without a reload.</p>
 */
@Controller
class PairController {

    private final AddressService addressService;
    private final QRService qrService;

    public PairController(AddressService addressService, QRService qrService) {
        this.addressService = addressService;
        this.qrService = qrService;
    }

    @GetMapping("/pair")
    public String pair(Model model) throws IOException {
        populate(model);
        return "pair";
    }

    @GetMapping("/pair/codes")
    public String codes(Model model) throws IOException {
        populate(model);
        return "fragments/pair :: codes";
    }

    private void populate(Model model) throws IOException {
        AddressService.Address address = addressService.current();
        String pairUri = addressService.pairUri();

        model.addAttribute("address", address);
        model.addAttribute("pairUri", pairUri);
        model.addAttribute("pairQr", qrService.generate(pairUri));
        model.addAttribute("serverQr", qrService.generate(address.url()));
        model.addAttribute("localOnly", addressService.isLocalOnly());
    }
}
