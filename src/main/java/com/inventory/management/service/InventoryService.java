
package com.inventory.management.service;

import com.inventory.management.dto.request.StockAdjustmentRequest;
import com.inventory.management.dto.response.InventorySummaryResponse;
import com.inventory.management.dto.response.StockBalanceResponse;
import com.inventory.management.dto.response.StockMovementResponse;

import com.inventory.management.entity.Product;
import com.inventory.management.entity.StockMovement;

import com.inventory.management.enums.StockMovementType;

import com.inventory.management.exception.ProductNotFoundException;

import com.inventory.management.repository.ProductRepository;
import com.inventory.management.repository.StockMovementRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public InventoryService(
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository) {

        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    // INCREASE STOCK

    public void increaseStock(
            Product product,
            int quantity,
            StockMovementType movementType,
            String referenceNumber,
            String remarks) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        if (movementType != StockMovementType.PURCHASE
                && movementType != StockMovementType.ADJUSTMENT_IN
                && movementType != StockMovementType.SALES_RETURN) {

            throw new IllegalArgumentException(
                    "Invalid stock IN movement type");
        }

        changeStock(
                product.getId(),
                quantity,
                movementType,
                referenceNumber,
                remarks);
    }

    // DECREASE STOCK

    public void decreaseStock(
            Product product,
            int quantity,
            StockMovementType movementType,
            String referenceNumber,
            String remarks) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        if (movementType != StockMovementType.SALE
                && movementType != StockMovementType.ADJUSTMENT_OUT
                && movementType != StockMovementType.PURCHASE_RETURN) {

            throw new IllegalArgumentException(
                    "Invalid stock OUT movement type");
        }

        changeStock(
                product.getId(),
                -quantity,
                movementType,
                referenceNumber,
                remarks);
    }

    // INITIALIZE OPENING STOCK

    public StockMovementResponse initializeStock(
            Long productId,
            int quantity,
            String remarks) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Opening stock quantity must be greater than zero");
        }

        Product product = getProductForUpdate(productId);

        int currentQuantity = product.getQuantity() == null
                ? 0
                : product.getQuantity();

        if (currentQuantity != 0) {
            throw new IllegalArgumentException(
                    "Opening stock can only be initialized when current stock is zero");
        }

        boolean hasPreviousMovement = !stockMovementRepository
                .findByProductIdOrderByCreatedAtDesc(productId)
                .isEmpty();

        if (hasPreviousMovement) {
            throw new IllegalArgumentException(
                    "Opening stock has already been initialized or stock movements already exist");
        }

        return changeStock(
                product,
                quantity,
                StockMovementType.OPENING_STOCK,
                null,
                remarks);
    }

    // STOCK ADJUSTMENT

    public StockMovementResponse adjustStock(
            StockAdjustmentRequest request) {

        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Adjustment quantity must be greater than zero");
        }

        if ("IN".equalsIgnoreCase(request.getAdjustmentType())) {

            return changeStock(
                    request.getProductId(),
                    request.getQuantity(),
                    StockMovementType.ADJUSTMENT_IN,
                    null,
                    request.getRemarks());

        } else if ("OUT".equalsIgnoreCase(
                request.getAdjustmentType())) {

            return changeStock(
                    request.getProductId(),
                    -request.getQuantity(),
                    StockMovementType.ADJUSTMENT_OUT,
                    null,
                    request.getRemarks());
        }

        throw new IllegalArgumentException(
                "Adjustment type must be IN or OUT");
    }

    // CHANGE STOCK

    private StockMovementResponse changeStock(
            Long productId,
            int quantityChange,
            StockMovementType movementType,
            String referenceNumber,
            String remarks) {

        Product product = getProductForUpdate(productId);

        return changeStock(
                product,
                quantityChange,
                movementType,
                referenceNumber,
                remarks);
    }

    private StockMovementResponse changeStock(
            Product product,
            int quantityChange,
            StockMovementType movementType,
            String referenceNumber,
            String remarks) {

        int quantityBefore = product.getQuantity() == null
                ? 0
                : product.getQuantity();

        int quantityAfter;

        try {
            quantityAfter = Math.addExact(
                    quantityBefore,
                    quantityChange);

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Stock quantity is too large");
        }

        if (quantityAfter < 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock for product: "
                            + product.getProductName());
        }

        product.setQuantity(quantityAfter);
        productRepository.save(product);

        StockMovement movement = new StockMovement();

        movement.setProduct(product);
        movement.setMovementType(movementType);
        movement.setQuantityChange(quantityChange);
        movement.setQuantityBefore(quantityBefore);
        movement.setQuantityAfter(quantityAfter);
        movement.setReferenceNumber(referenceNumber);
        movement.setRemarks(remarks);

        StockMovement savedMovement =
                stockMovementRepository.save(movement);

        return convertToStockMovementResponse(savedMovement);
    }

    // GET PRODUCT FOR UPDATE

    private Product getProductForUpdate(Long productId) {

        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with ID: " + productId));
    }

    // STOCK HISTORY

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getProductStockHistory(
            Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(
                    "Product not found with ID: " + productId);
        }

        return stockMovementRepository
                .findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::convertToStockMovementResponse)
                .toList();
    }

    // LOW STOCK PRODUCTS

    // LOW STOCK PRODUCTS

    @Transactional(readOnly = true)
    public List<StockBalanceResponse> getLowStockProducts() {

        return productRepository.findAllByOrderByProductNameAsc()
                .stream()
                .filter(product ->
                        Boolean.TRUE.equals(product.getActive())
                                && product.getQuantity() != null
                                && product.getMinimumStock() != null
                                && product.getQuantity() > 0
                                && product.getQuantity()
                                <= product.getMinimumStock())
                .map(this::convertToStockBalanceResponse)
                .toList();
    }

    // GET ALL STOCK BALANCES

    @Transactional(readOnly = true)
    public List<StockBalanceResponse> getAllStockBalances() {

        return productRepository.findAllByOrderByProductNameAsc()
                .stream()
                .map(this::convertToStockBalanceResponse)
                .toList();
    }

    // GET STOCK BALANCE BY PRODUCT ID

    @Transactional(readOnly = true)
    public StockBalanceResponse getStockBalanceByProductId(
            Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with ID: " + productId));

        return convertToStockBalanceResponse(product);
    }

    // INVENTORY SUMMARY

    @Transactional(readOnly = true)
    public InventorySummaryResponse getInventorySummary() {

        List<Product> products = productRepository.findAll();

        long totalProducts = products.size();
        long activeProducts = 0;
        long inactiveProducts = 0;
        long totalStockQuantity = 0;
        long lowStockCount = 0;
        long outOfStockCount = 0;

        BigDecimal totalInventoryValue = BigDecimal.ZERO;

        for (Product product : products) {

            int quantity = product.getQuantity() == null
                    ? 0
                    : product.getQuantity();

            int minimumStock = product.getMinimumStock() == null
                    ? 0
                    : product.getMinimumStock();

            BigDecimal costPrice = product.getCostPrice() == null
                    ? BigDecimal.ZERO
                    : product.getCostPrice();

            totalStockQuantity += quantity;

            totalInventoryValue = totalInventoryValue.add(
                    costPrice.multiply(BigDecimal.valueOf(quantity))
            );

            boolean active = Boolean.TRUE.equals(product.getActive());

            if (active) {

                activeProducts++;

                if (quantity == 0) {
                    outOfStockCount++;

                } else if (quantity <= minimumStock) {
                    lowStockCount++;
                }

            } else {
                inactiveProducts++;
            }
        }

        InventorySummaryResponse response =
                new InventorySummaryResponse();

        response.setTotalProducts(totalProducts);
        response.setActiveProducts(activeProducts);
        response.setInactiveProducts(inactiveProducts);
        response.setTotalStockQuantity(totalStockQuantity);
        response.setTotalInventoryValue(totalInventoryValue);
        response.setLowStockCount(lowStockCount);
        response.setOutOfStockCount(outOfStockCount);

        return response;
    }

    // CONVERT STOCK BALANCE RESPONSE

    private StockBalanceResponse convertToStockBalanceResponse(
            Product product) {

        StockBalanceResponse response = new StockBalanceResponse();

        int quantity = product.getQuantity() == null
                ? 0
                : product.getQuantity();

        int minimumStock = product.getMinimumStock() == null
                ? 0
                : product.getMinimumStock();

        BigDecimal costPrice = product.getCostPrice() == null
                ? BigDecimal.ZERO
                : product.getCostPrice();

        BigDecimal stockValue = costPrice.multiply(
                BigDecimal.valueOf(quantity));

        String stockStatus;

        if (quantity == 0) {
            stockStatus = "OUT_OF_STOCK";

        } else if (quantity <= minimumStock) {
            stockStatus = "LOW_STOCK";

        } else {
            stockStatus = "NORMAL";
        }

        response.setProductId(product.getId());
        response.setProductCode(product.getProductCode());
        response.setProductName(product.getProductName());
        response.setCurrentQuantity(quantity);
        response.setMinimumStock(minimumStock);
        response.setStockStatus(stockStatus);
        response.setCostPrice(costPrice);
        response.setStockValue(stockValue);

        return response;
    }

    // CONVERT STOCK MOVEMENT RESPONSE

    private StockMovementResponse convertToStockMovementResponse(
            StockMovement movement) {

        StockMovementResponse response = new StockMovementResponse();

        response.setId(movement.getId());
        response.setProductId(movement.getProduct().getId());
        response.setProductCode(
                movement.getProduct().getProductCode());
        response.setProductName(
                movement.getProduct().getProductName());
        response.setMovementType(movement.getMovementType());
        response.setQuantityChange(movement.getQuantityChange());
        response.setQuantityBefore(movement.getQuantityBefore());
        response.setQuantityAfter(movement.getQuantityAfter());
        response.setRemarks(movement.getRemarks());
        response.setReferenceNumber(movement.getReferenceNumber());
        response.setCreatedAt(movement.getCreatedAt());

        return response;
    }
}
