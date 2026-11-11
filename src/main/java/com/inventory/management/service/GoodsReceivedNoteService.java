package com.inventory.management.service;

import com.inventory.management.dto.request.GoodsReceivedNoteItemRequest;
import com.inventory.management.dto.request.GoodsReceivedNoteRequest;
import com.inventory.management.dto.response.GoodsReceivedNoteItemResponse;
import com.inventory.management.dto.response.GoodsReceivedNoteResponse;
import com.inventory.management.entity.*;
import com.inventory.management.enums.PurchaseStatus;
import com.inventory.management.enums.StockMovementType;
import com.inventory.management.exception.InvalidPurchaseOperationException;
import com.inventory.management.exception.PurchaseNotFoundException;
import com.inventory.management.repository.GoodsReceivedNoteRepository;
import com.inventory.management.repository.PurchaseRepository;
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class GoodsReceivedNoteService {

    private final GoodsReceivedNoteRepository grnRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryService inventoryService;

    public GoodsReceivedNoteService(
            GoodsReceivedNoteRepository grnRepository,
            PurchaseRepository purchaseRepository,
            InventoryService inventoryService
    ) {
        this.grnRepository = grnRepository;
        this.purchaseRepository = purchaseRepository;
        this.inventoryService = inventoryService;
    }

    public GoodsReceivedNoteResponse receive(
            GoodsReceivedNoteRequest request
    ) {
        validateRequest(request);

        Map<Long, Integer> requestedItems = normalizeItems(request);

        Optional<GoodsReceivedNote> existing =
                grnRepository.findByRequestKey(request.getRequestKey());

        if (existing.isPresent()) {
            return returnExistingOrReject(
                    existing.get(),
                    request.getPurchaseId(),
                    requestedItems
            );
        }

        Purchase purchase = purchaseRepository
                .findByIdForUpdate(request.getPurchaseId())
                .orElseThrow(() -> new PurchaseNotFoundException(
                        "Purchase not found with ID: "
                                + request.getPurchaseId()
                ));

        existing = grnRepository.findByRequestKey(request.getRequestKey());

        if (existing.isPresent()) {
            return returnExistingOrReject(
                    existing.get(),
                    request.getPurchaseId(),
                    requestedItems
            );
        }

        if (purchase.getStatus() != PurchaseStatus.DRAFT
                && purchase.getStatus() != PurchaseStatus.PARTIALLY_RECEIVED) {
            throw new InvalidPurchaseOperationException(
                    "Only DRAFT or PARTIALLY_RECEIVED purchases can receive goods"
            );
        }

        Map<Long, PurchaseItem> purchaseItems = new HashMap<>();

        for (PurchaseItem item : purchase.getItems()) {
            purchaseItems.put(item.getId(), item);
        }

        for (Map.Entry<Long, Integer> entry : requestedItems.entrySet()) {

            PurchaseItem purchaseItem = purchaseItems.get(entry.getKey());

            if (purchaseItem == null) {
                throw new InvalidPurchaseOperationException(
                        "Purchase item does not belong to this purchase: "
                                + entry.getKey()
                );
            }

            if (purchaseItem.getProduct() == null
                    || !Boolean.TRUE.equals(
                    purchaseItem.getProduct().getActive())) {
                throw new InvalidPurchaseOperationException(
                        "Cannot receive inactive or missing product"
                );
            }

            int remaining = purchaseItem.getRemainingQuantity();

            if (entry.getValue() > remaining) {
                throw new InvalidPurchaseOperationException(
                        "Receiving quantity exceeds remaining quantity for "
                                + purchaseItem.getProductNameSnapshot()
                                + ". Remaining: " + remaining
                );
            }
        }

        GoodsReceivedNote grn = new GoodsReceivedNote();
        grn.setGrnNumber(BusinessCodeGenerator.temporary());
        grn.setRequestKey(request.getRequestKey().trim());
        grn.setPurchase(purchase);

        GoodsReceivedNote savedGrn = grnRepository.saveAndFlush(grn);

        savedGrn.setGrnNumber(
                BusinessCodeGenerator.format("GRN", savedGrn.getId())
        );

        grnRepository.saveAndFlush(savedGrn);

        for (Map.Entry<Long, Integer> entry : requestedItems.entrySet()) {

            PurchaseItem purchaseItem = purchaseItems.get(entry.getKey());
            int receivedNow = entry.getValue();

            GoodsReceivedNoteItem grnItem = new GoodsReceivedNoteItem();
            grnItem.setGoodsReceivedNote(savedGrn);
            grnItem.setPurchaseItem(purchaseItem);
            grnItem.setProductCode(
                    purchaseItem.getProductCodeSnapshot()
            );
            grnItem.setProductName(
                    purchaseItem.getProductNameSnapshot()
            );
            grnItem.setQuantity(receivedNow);

            savedGrn.getItems().add(grnItem);

            purchaseItem.setReceivedQuantity(
                    purchaseItem.getReceivedQuantity() + receivedNow
            );

            inventoryService.increaseStock(
                    purchaseItem.getProduct(),
                    receivedNow,
                    StockMovementType.PURCHASE,
                    savedGrn.getGrnNumber(),
                    "Goods received from " + purchase.getPurchaseNumber()
            );
        }

        boolean fullyReceived = purchase.getItems()
                .stream()
                .allMatch(item -> item.getRemainingQuantity() == 0);

        purchase.setStatus(
                fullyReceived
                        ? PurchaseStatus.RECEIVED
                        : PurchaseStatus.PARTIALLY_RECEIVED
        );

        grnRepository.save(savedGrn);
        purchaseRepository.save(purchase);

        return convertToResponse(savedGrn);
    }

    @Transactional(readOnly = true)
    public GoodsReceivedNoteResponse getById(Long id) {

        GoodsReceivedNote grn = grnRepository.findById(id)
                .orElseThrow(() -> new InvalidPurchaseOperationException(
                        "GRN not found with ID: " + id
                ));

        return convertToResponse(grn);
    }

    @Transactional(readOnly = true)
    public List<GoodsReceivedNoteResponse> getByPurchaseId(Long purchaseId) {

        return grnRepository
                .findAllByPurchase_IdOrderByReceivedAtDesc(purchaseId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private void validateRequest(GoodsReceivedNoteRequest request) {

        if (request == null
                || request.getPurchaseId() == null
                || request.getPurchaseId() <= 0) {
            throw new InvalidPurchaseOperationException(
                    "Valid purchase ID is required"
            );
        }

        if (request.getRequestKey() == null
                || request.getRequestKey().isBlank()
                || request.getRequestKey().length() > 80) {
            throw new InvalidPurchaseOperationException(
                    "A valid requestKey is required (maximum 80 characters)"
            );
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidPurchaseOperationException(
                    "At least one receiving item is required"
            );
        }
    }

    private Map<Long, Integer> normalizeItems(
            GoodsReceivedNoteRequest request
    ) {
        Map<Long, Integer> result = new LinkedHashMap<>();

        for (GoodsReceivedNoteItemRequest item : request.getItems()) {

            if (item == null
                    || item.getPurchaseItemId() == null
                    || item.getPurchaseItemId() <= 0
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Every receiving item needs a valid purchaseItemId and quantity"
                );
            }

            if (result.putIfAbsent(
                    item.getPurchaseItemId(),
                    item.getQuantity()
            ) != null) {
                throw new InvalidPurchaseOperationException(
                        "Duplicate purchaseItemId in GRN request"
                );
            }
        }

        return result;
    }

    private GoodsReceivedNoteResponse returnExistingOrReject(
            GoodsReceivedNote existing,
            Long purchaseId,
            Map<Long, Integer> requestedItems
    ) {
        if (!existing.getPurchase().getId().equals(purchaseId)) {
            throw new InvalidPurchaseOperationException(
                    "requestKey has already been used for another purchase"
            );
        }

        Map<Long, Integer> existingItems = new LinkedHashMap<>();

        for (GoodsReceivedNoteItem item : existing.getItems()) {
            existingItems.put(
                    item.getPurchaseItem().getId(),
                    item.getQuantity()
            );
        }

        if (!existingItems.equals(requestedItems)) {
            throw new InvalidPurchaseOperationException(
                    "requestKey was already used with a different GRN request"
            );
        }

        return convertToResponse(existing);
    }

    private GoodsReceivedNoteResponse convertToResponse(
            GoodsReceivedNote grn
    ) {
        GoodsReceivedNoteResponse response =
                new GoodsReceivedNoteResponse();

        response.setId(grn.getId());
        response.setGrnNumber(grn.getGrnNumber());
        response.setPurchaseId(grn.getPurchase().getId());
        response.setPurchaseNumber(
                grn.getPurchase().getPurchaseNumber()
        );
        response.setSupplierName(
                grn.getPurchase().getSupplier().getSupplierName()
        );
        response.setReceivedAt(grn.getReceivedAt());

        List<GoodsReceivedNoteItemResponse> items =
                grn.getItems().stream()
                        .map(item -> {
                            GoodsReceivedNoteItemResponse itemResponse =
                                    new GoodsReceivedNoteItemResponse();

                            itemResponse.setPurchaseItemId(
                                    item.getPurchaseItem().getId()
                            );
                            itemResponse.setProductCode(
                                    item.getProductCode()
                            );
                            itemResponse.setProductName(
                                    item.getProductName()
                            );
                            itemResponse.setQuantity(item.getQuantity());

                            return itemResponse;
                        })
                        .toList();

        response.setItems(items);

        return response;
    }
}