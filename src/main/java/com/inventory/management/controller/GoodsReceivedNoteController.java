package com.inventory.management.controller;

import com.inventory.management.dto.request.GoodsReceivedNoteRequest;
import com.inventory.management.dto.response.GoodsReceivedNoteResponse;
import com.inventory.management.service.GoodsReceivedNoteService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grn")
@CrossOrigin(origins = "*")
public class GoodsReceivedNoteController {

    private final GoodsReceivedNoteService grnService;

    public GoodsReceivedNoteController(
            GoodsReceivedNoteService grnService
    ) {
        this.grnService = grnService;
    }

    @PostMapping
    public ResponseEntity<GoodsReceivedNoteResponse> receive(
            @Valid @RequestBody GoodsReceivedNoteRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(grnService.receive(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoodsReceivedNoteResponse> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(grnService.getById(id));
    }

    @GetMapping("/purchase/{purchaseId}")
    public ResponseEntity<List<GoodsReceivedNoteResponse>> getByPurchaseId(
            @PathVariable Long purchaseId
    ) {
        return ResponseEntity.ok(
                grnService.getByPurchaseId(purchaseId)
        );
    }
}