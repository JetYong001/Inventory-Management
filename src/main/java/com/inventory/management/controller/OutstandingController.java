package com.inventory.management.controller;

import com.inventory.management.dto.response.OutstandingResponse;
import com.inventory.management.dto.response.OutstandingSummaryResponse;
import com.inventory.management.service.OutstandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outstanding")
@CrossOrigin(origins = "*")
public class OutstandingController {

    private final OutstandingService outstandingService;

    public OutstandingController(OutstandingService outstandingService) {
        this.outstandingService = outstandingService;
    }

    @GetMapping("/summary")
    public ResponseEntity<OutstandingSummaryResponse> getOutstandingSummary() {

        return ResponseEntity.ok(
                outstandingService.getOutstandingSummary()
        );
    }

    @GetMapping("/customers")
    public ResponseEntity<List<OutstandingResponse>> getCustomerOutstandings() {

        return ResponseEntity.ok(
                outstandingService.getCustomerOutstandings()
        );
    }

    @GetMapping("/suppliers")
    public ResponseEntity<List<OutstandingResponse>> getSupplierOutstandings() {

        return ResponseEntity.ok(
                outstandingService.getSupplierOutstandings()
        );
    }
}