package com.dhruv.incident_copilot.controller;

import com.dhruv.incident_copilot.dto.PostmortemRequest;
import com.dhruv.incident_copilot.dto.PostmortemResponse;
import com.dhruv.incident_copilot.service.PostmortemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/postmortems")
public class PostmortemController {

    private final PostmortemService postmortemService;

    public PostmortemController(PostmortemService postmortemService) {
        this.postmortemService = postmortemService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PostmortemResponse> create(@Valid @RequestBody PostmortemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postmortemService.create(request));
    }

    @GetMapping
    public List<PostmortemResponse> getAll() {
        return postmortemService.getAll();
    }
}
