package com.inventory.management.service;

import com.inventory.management.dto.request.PurchaseItemRequest;
import com.inventory.management.dto.request.PurchaseRequest;
import com.inventory.management.dto.response.PurchaseItemResponse;
import com.inventory.management.dto.response.PurchaseResponse;

import com.inventory.management.entity.Product;
import com.inventory.management.entity.Purchase;
import com.inventory.management.entity.PurchaseItem;
import com.inventory.management.entity.Supplier;

import com.inventory.management.enums.PurchaseStatus;

import com.inventory.management.exception.InvalidPurchaseOperationException;
import com.inventory.management.exception.ProductNotFoundException;
import com.inventory.management.exception.PurchaseNotFoundException;
import com.inventory.management.exception.SupplierNotFoundException;

import com.inventory.management.repository.ProductRepository;
import com.inventory.management.repository.PurchaseRepository;
import com.inventory.management.repository.SupplierRepository;

import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            ProductRepository productRepository,
            SupplierRepository supplierRepository
    ) {
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
    }

    // =========================
    // CREATE PURCHASE
    // =========================

    public PurchaseResponse createPurchase(PurchaseRequest request) {

        if (request == null) {
            throw new InvalidPurchaseOperationException(
                    "Purchase request is required"
            );
        }

        if (request.getSupplierId() == null
                || request.getSupplierId() <= 0) {
            throw new InvalidPurchaseOperationException(
                    "Valid supplier ID is required"
            );
        }

        if (request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new InvalidPurchaseOperationException(
                    "Purchase must contain at least one item"
            );
        }

        Supplier supplier = supplierRepository
                .findById(request.getSupplierId())
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found with ID: "
                                + request.getSupplierId()
                ));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new InvalidPurchaseOperationException(
                    "Cannot create purchase for an inactive supplier"
            );
        }

        Purchase purchase = new Purchase();

        purchase.setPurchaseNumber(
                BusinessCodeGenerator.temporary()
        );

        purchase.setSupplier(supplier);
        purchase.setStatus(PurchaseStatus.DRAFT);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PurchaseItemRequest itemRequest : request.getItems()) {

            if (itemRequest == null
                    || itemRequest.getProductId() == null
                    || itemRequest.getProductId() <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Valid product ID is required"
                );
            }

            if (itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Purchase quantity must be greater than zero"
                );
            }

            if (itemRequest.getUnitCost() == null
                    || itemRequest.getUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Unit cost must be greater than zero"
                );
            }

            Product product = productRepository
                    .findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with ID: "
                                    + itemRequest.getProductId()
                    ));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidPurchaseOperationException(
                        "Cannot purchase an inactive product: "
                                + product.getProductName()
                );
            }

            PurchaseItem item = new PurchaseItem();

            item.setPurchase(purchase);
            item.setProduct(product);

            item.setProductCodeSnapshot(
                    product.getProductCode()
            );

            item.setProductNameSnapshot(
                    product.getProductName()
            );

            item.setQuantity(itemRequest.getQuantity());
            item.setReceivedQuantity(0);
            item.setUnitCost(itemRequest.getUnitCost());

            BigDecimal subtotal = itemRequest.getUnitCost()
                    .multiply(
                            BigDecimal.valueOf(itemRequest.getQuantity())
                    );

            item.setSubtotal(subtotal);

            purchase.getItems().add(item);

            totalAmount = totalAmount.add(subtotal);
        }

        purchase.setTotalAmount(totalAmount);

        Purchase savedPurchase =
                purchaseRepository.saveAndFlush(purchase);

        savedPurchase.setPurchaseNumber(
                BusinessCodeGenerator.format(
                        "PO",
                        savedPurchase.getId()
                )
        );

        savedPurchase =
                purchaseRepository.saveAndFlush(savedPurchase);

        return convertToResponse(savedPurchase);
    }

    // =========================
    // GET ALL PURCHASES
    // =========================

    @Transactional(readOnly = true)
    public List<PurchaseResponse> getAllPurchases() {

        return purchaseRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // =========================
    // GET PURCHASE BY ID
    // =========================

    @Transactional(readOnly = true)
    public PurchaseResponse getPurchaseById(Long id) {

        return convertToResponse(
                findPurchaseById(id)
        );
    }

    // =========================
    // CANCEL PURCHASE
    // =========================

    public PurchaseResponse cancelPurchase(Long id) {

        Purchase purchase = findPurchaseByIdForUpdate(id);

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new InvalidPurchaseOperationException(
                    "Only DRAFT purchases can be cancelled"
            );
        }

        purchase.setStatus(PurchaseStatus.CANCELLED);

        return convertToResponse(
                purchaseRepository.save(purchase)
        );
    }

    // =========================
    // UPDATE PURCHASE
    // =========================

    public PurchaseResponse updatePurchase(
            Long id,
            PurchaseRequest request
    ) {

        if (request == null) {
            throw new InvalidPurchaseOperationException(
                    "Purchase request is required"
            );
        }

        if (request.getSupplierId() == null
                || request.getSupplierId() <= 0) {
            throw new InvalidPurchaseOperationException(
                    "Valid supplier ID is required"
            );
        }

        if (request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new InvalidPurchaseOperationException(
                    "Purchase must contain at least one item"
            );
        }

        Purchase purchase = findPurchaseByIdForUpdate(id);

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new InvalidPurchaseOperationException(
                    "Only DRAFT purchases can be updated"
            );
        }

        Supplier supplier = supplierRepository
                .findById(request.getSupplierId())
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found with ID: "
                                + request.getSupplierId()
                ));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new InvalidPurchaseOperationException(
                    "Cannot update purchase with an inactive supplier"
            );
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        List<PurchaseItem> newItems = new ArrayList<>();

        for (PurchaseItemRequest itemRequest : request.getItems()) {

            if (itemRequest == null
                    || itemRequest.getProductId() == null
                    || itemRequest.getProductId() <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Valid product ID is required"
                );
            }

            if (itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Purchase quantity must be greater than zero"
                );
            }

            if (itemRequest.getUnitCost() == null
                    || itemRequest.getUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPurchaseOperationException(
                        "Unit cost must be greater than zero"
                );
            }

            Product product = productRepository
                    .findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with ID: "
                                    + itemRequest.getProductId()
                    ));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidPurchaseOperationException(
                        "Cannot purchase an inactive product: "
                                + product.getProductName()
                );
            }

            PurchaseItem item = new PurchaseItem();

            item.setPurchase(purchase);
            item.setProduct(product);

            item.setProductCodeSnapshot(
                    product.getProductCode()
            );

            item.setProductNameSnapshot(
                    product.getProductName()
            );

            item.setQuantity(itemRequest.getQuantity());
            item.setReceivedQuantity(0);
            item.setUnitCost(itemRequest.getUnitCost());

            BigDecimal subtotal = itemRequest.getUnitCost()
                    .multiply(
                            BigDecimal.valueOf(itemRequest.getQuantity())
                    );

            item.setSubtotal(subtotal);

            newItems.add(item);

            totalAmount = totalAmount.add(subtotal);
        }

        purchase.setSupplier(supplier);

        purchase.getItems().clear();
        purchase.getItems().addAll(newItems);

        purchase.setTotalAmount(totalAmount);

        return convertToResponse(
                purchaseRepository.save(purchase)
        );
    }

    // =========================
    // FIND PURCHASE
    // =========================

    private Purchase findPurchaseById(Long id) {

        if (id == null || id <= 0) {
            throw new InvalidPurchaseOperationException(
                    "Valid purchase ID is required"
            );
        }

        return purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(
                        "Purchase not found with ID: " + id
                ));
    }

    private Purchase findPurchaseByIdForUpdate(Long id) {

        if (id == null || id <= 0) {
            throw new InvalidPurchaseOperationException(
                    "Valid purchase ID is required"
            );
        }

        return purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PurchaseNotFoundException(
                        "Purchase not found with ID: " + id
                ));
    }

    // =========================
    // RESPONSE MAPPING
    // =========================

    private PurchaseResponse convertToResponse(Purchase purchase) {

        PurchaseResponse response = new PurchaseResponse();

        response.setId(purchase.getId());
        response.setPurchaseNumber(purchase.getPurchaseNumber());

        response.setSupplierId(
                purchase.getSupplier().getId()
        );

        response.setSupplierName(
                purchase.getSupplier().getSupplierName()
        );

        response.setPurchaseDate(
                purchase.getPurchaseDate()
        );

        response.setTotalAmount(
                purchase.getTotalAmount()
        );

        response.setStatus(
                purchase.getStatus()
        );

        List<PurchaseItemResponse> items = purchase.getItems()
                .stream()
                .map(item -> {

                    PurchaseItemResponse itemResponse =
                            new PurchaseItemResponse();

                    itemResponse.setId(item.getId());

                    itemResponse.setProductId(
                            item.getProduct().getId()
                    );

                    itemResponse.setProductCode(
                            item.getProductCodeSnapshot() != null
                                    ? item.getProductCodeSnapshot()
                                    : item.getProduct().getProductCode()
                    );

                    itemResponse.setProductName(
                            item.getProductNameSnapshot() != null
                                    ? item.getProductNameSnapshot()
                                    : item.getProduct().getProductName()
                    );

                    itemResponse.setQuantity(
                            item.getQuantity()
                    );

                    itemResponse.setReceivedQuantity(
                            item.getReceivedQuantity()
                    );

                    itemResponse.setRemainingQuantity(
                            item.getRemainingQuantity()
                    );

                    itemResponse.setUnitCost(
                            item.getUnitCost()
                    );

                    itemResponse.setSubtotal(
                            item.getSubtotal()
                    );

                    return itemResponse;
                })
                .toList();

        response.setItems(items);

        return response;
    }
}