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
import com.inventory.management.enums.StockMovementType;
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
    private final InventoryService inventoryService;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            InventoryService inventoryService) {

        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryService = inventoryService;
    }

    public PurchaseResponse createPurchase(PurchaseRequest request) {

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found with ID: " + request.getSupplierId()
                ));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new InvalidPurchaseOperationException(
                    "Cannot create purchase for an inactive supplier"
            );
        }

        Purchase purchase = new Purchase();
        purchase.setPurchaseNumber(BusinessCodeGenerator.temporary());
        purchase.setSupplier(supplier);
        purchase.setStatus(PurchaseStatus.DRAFT);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (PurchaseItemRequest itemRequest : request.getItems()) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with ID: " + itemRequest.getProductId()
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
            item.setProductCodeSnapshot(product.getProductCode());
            item.setProductNameSnapshot(product.getProductName());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitCost(itemRequest.getUnitCost());

            BigDecimal subtotal = itemRequest.getUnitCost()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            item.setSubtotal(subtotal);
            purchase.getItems().add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        purchase.setTotalAmount(totalAmount);

        Purchase savedPurchase = purchaseRepository.saveAndFlush(purchase);

        savedPurchase.setPurchaseNumber(
                BusinessCodeGenerator.format("PO", savedPurchase.getId())
        );

        savedPurchase = purchaseRepository.saveAndFlush(savedPurchase);

        return convertToResponse(savedPurchase);
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> getAllPurchases() {

        return purchaseRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getPurchaseById(Long id) {

        return convertToResponse(findPurchaseById(id));
    }

    public PurchaseResponse receivePurchase(Long id) {

        Purchase purchase = findPurchaseByIdForUpdate(id);

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new InvalidPurchaseOperationException(
                    "Only DRAFT purchases can be received"
            );
        }

        for (PurchaseItem item : purchase.getItems()) {

            Product product = item.getProduct();

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidPurchaseOperationException(
                        "Cannot receive purchase because product is inactive: "
                                + product.getProductName()
                );
            }

            inventoryService.increaseStock(
                    product,
                    item.getQuantity(),
                    StockMovementType.PURCHASE,
                    purchase.getPurchaseNumber(),
                    "Purchase received"
            );
        }

        purchase.setStatus(PurchaseStatus.RECEIVED);

        return convertToResponse(purchaseRepository.save(purchase));
    }

    public PurchaseResponse cancelPurchase(Long id) {

        Purchase purchase = findPurchaseByIdForUpdate(id);

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new InvalidPurchaseOperationException(
                    "Only DRAFT purchases can be cancelled"
            );
        }

        purchase.setStatus(PurchaseStatus.CANCELLED);

        return convertToResponse(purchaseRepository.save(purchase));
    }

    public PurchaseResponse updatePurchase(Long id, PurchaseRequest request) {

        Purchase purchase = findPurchaseByIdForUpdate(id);

        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new InvalidPurchaseOperationException(
                    "Only DRAFT purchases can be updated"
            );
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new SupplierNotFoundException(
                        "Supplier not found with ID: " + request.getSupplierId()
                ));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new InvalidPurchaseOperationException(
                    "Cannot update purchase with an inactive supplier"
            );
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseItem> newItems = new ArrayList<>();

        for (PurchaseItemRequest itemRequest : request.getItems()) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with ID: " + itemRequest.getProductId()
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
            item.setProductCodeSnapshot(product.getProductCode());
            item.setProductNameSnapshot(product.getProductName());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitCost(itemRequest.getUnitCost());

            BigDecimal subtotal = itemRequest.getUnitCost()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            item.setSubtotal(subtotal);
            newItems.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        purchase.setSupplier(supplier);
        purchase.getItems().clear();
        purchase.getItems().addAll(newItems);
        purchase.setTotalAmount(totalAmount);

        return convertToResponse(purchaseRepository.save(purchase));
    }

    private Purchase findPurchaseById(Long id) {

        return purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(
                        "Purchase not found with ID: " + id
                ));
    }

    private Purchase findPurchaseByIdForUpdate(Long id) {

        return purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PurchaseNotFoundException(
                        "Purchase not found with ID: " + id
                ));
    }

    private PurchaseResponse convertToResponse(Purchase purchase) {

        PurchaseResponse response = new PurchaseResponse();

        response.setId(purchase.getId());
        response.setPurchaseNumber(purchase.getPurchaseNumber());
        response.setSupplierId(purchase.getSupplier().getId());
        response.setSupplierName(purchase.getSupplier().getSupplierName());
        response.setPurchaseDate(purchase.getPurchaseDate());
        response.setTotalAmount(purchase.getTotalAmount());
        response.setStatus(purchase.getStatus());

        List<PurchaseItemResponse> items = purchase.getItems()
                .stream()
                .map(item -> {

                    PurchaseItemResponse itemResponse = new PurchaseItemResponse();

                    itemResponse.setId(item.getId());
                    itemResponse.setProductId(item.getProduct().getId());
                    itemResponse.setProductCode(item.getProduct().getProductCode());
                    itemResponse.setProductName(item.getProduct().getProductName());
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setUnitCost(item.getUnitCost());
                    itemResponse.setSubtotal(item.getSubtotal());

                    return itemResponse;
                })
                .toList();

        response.setItems(items);

        return response;
    }
}