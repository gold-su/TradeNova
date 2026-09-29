package com.tradenova.waitlist.controller;

import com.tradenova.waitlist.dto.BetaWaitlistRequest;
import com.tradenova.waitlist.dto.BetaWaitlistResponse;
import com.tradenova.waitlist.service.BetaWaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/waitlist")
public class BetaWaitlistController {
    private final BetaWaitlistService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BetaWaitlistResponse create(@Valid @RequestBody BetaWaitlistRequest request) {
        return service.create(request);
    }
}
