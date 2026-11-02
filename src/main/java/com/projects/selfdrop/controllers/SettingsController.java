package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.UpdateAddressRequest;
import com.projects.selfdrop.dtos.responses.AddressDto;
import com.projects.selfdrop.services.AddressService;
import com.projects.selfdrop.services.WorkerPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Lets the machine's owner review and override the address this server
 * advertises to clients.
 *
 * <p>Overriding the address changes where pairing clients are sent, so both
 * endpoints only answer requests from the machine itself. The Worker write
 * token stays in the backend: this controller only asks the publisher to push
 * the new address.</p>
 */
@RestController
@RequestMapping("/settings")
class SettingsController {

    private final AddressService addressService;
    private final WorkerPublisher workerPublisher;

    public SettingsController(AddressService addressService, WorkerPublisher workerPublisher) {
        this.addressService = addressService;
        this.workerPublisher = workerPublisher;
    }

    @GetMapping("/address")
    public ResponseEntity<?> address(HttpServletRequest request) {
        if (isLoopback(request)) {
            return forbidden();
        }
        return ResponseEntity.ok(dto(addressService.current()));
    }

    @PostMapping("/address")
    public ResponseEntity<?> update(
            @Valid @RequestBody UpdateAddressRequest update,
            HttpServletRequest request) {
        if (isLoopback(request)) {
            return forbidden();
        }

        String url = update.url().strip();
        if (!WorkerPublisher.isPrivateHttpUrl(url)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "The address must be a private http://<IPv4>:<port> URL.");
            problem.setTitle("Invalid address");
            return ResponseEntity.badRequest().body(problem);
        }

        AddressService.Address current = addressService.current();
        AddressService.Address updated = addressService.override(
                new AddressService.Address(url, current.serverId()));
        workerPublisher.publishIfChanged();

        return ResponseEntity.ok(dto(updated));
    }

    private AddressDto dto(AddressService.Address address) {
        return new AddressDto(address.url(), address.serverId(), addressService.isLocalOnly());
    }

    private static ResponseEntity<ProblemDetail> forbidden() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Settings are only available on the server itself.");
        problem.setTitle("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    private static boolean isLoopback(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return true;
        }
        try {
            return !InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
