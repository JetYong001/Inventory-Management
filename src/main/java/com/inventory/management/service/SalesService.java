package com.inventory.management.service;

import com.inventory.management.dto.request.SalesItemRequest;
import com.inventory.management.dto.request.SalesRequest;
import com.inventory.management.dto.response.SalesItemResponse;
import com.inventory.management.dto.response.SalesResponse;
import com.inventory.management.entity.Customer;
import com.inventory.management.entity.Product;
import com.inventory.management.entity.Sales;
import com.inventory.management.entity.SalesItem;
import com.inventory.management.enums.SalesStatus;
import com.inventory.management.enums.StockMovementType;
import com.inventory.management.exception.CustomerNotFoundException;
import com.inventory.management.exception.InvalidSalesOperationException;
import com.inventory.management.exception.ProductNotFoundException;
import com.inventory.management.exception.SalesNotFoundException;
import com.inventory.management.repository.CustomerRepository;
import com.inventory.management.repository.ProductRepository;
import com.inventory.management.repository.SalesRepository;
import com.inventory.management.util.BusinessCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SalesService {

    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final SalesInvoiceService salesInvoiceService;

    public SalesService(
            SalesRepository salesRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            InventoryService inventoryService,
            SalesInvoiceService salesInvoiceService) {

        this.salesRepository = salesRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.salesInvoiceService = salesInvoiceService;
    }

    public SalesResponse createSales(SalesRequest request) {

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found with ID: " + request.getCustomerId()
                ));

        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw new InvalidSalesOperationException(
                    "Cannot create sales for an inactive customer"
            );
        }

        Sales sales = new Sales();
        sales.setSalesNumber(BusinessCodeGenerator.temporary());
        sales.setCustomer(customer);
        sales.setStatus(SalesStatus.DRAFT);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SalesItemRequest itemRequest : request.getItems()) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with ID: " + itemRequest.getProductId()
                    ));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidSalesOperationException(
                        "Cannot sell an inactive product: "
                                + product.getProductName()
                );
            }

            SalesItem item = new SalesItem();
            item.setSales(sales);
            item.setProduct(product);
            item.setProductCodeSnapshot(product.getProductCode());
            item.setProductNameSnapshot(product.getProductName());
            item.setQuantity(itemRequest.getQuantity());

            BigDecimal unitPrice = product.getSellingPrice();
            item.setUnitPrice(unitPrice);

            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            item.setSubtotal(subtotal);
            sales.getItems().add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        sales.setTotalAmount(totalAmount);

        Sales savedSales = salesRepository.saveAndFlush(sales);

        savedSales.setSalesNumber(
                BusinessCodeGenerator.format("SO", savedSales.getId())
        );

        savedSales = salesRepository.saveAndFlush(savedSales);

        return convertToResponse(savedSales);
    }

    @Transactional(readOnly = true)
    public List<SalesResponse> getAllSales() {

        return salesRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalesResponse getSalesById(Long id) {

        return convertToResponse(findSalesById(id));
    }

    public SalesResponse completeSales(Long id) {

        Sales sales = findSalesByIdForUpdate(id);

        if (sales.getStatus() != SalesStatus.DRAFT) {
            throw new InvalidSalesOperationException(
                    "Only DRAFT sales can be completed"
            );
        }

        if (sales.getItems() == null || sales.getItems().isEmpty()) {
            throw new InvalidSalesOperationException(
                    "Cannot complete sales without items"
            );
        }

        Map<Product, Integer> productQuantities = new LinkedHashMap<>();

        for (SalesItem item : sales.getItems()) {

            Product product = item.getProduct();

            productQuantities.merge(
                    product,
                    item.getQuantity(),
                    Integer::sum
            );
        }

        for (Map.Entry<Product, Integer> entry : productQuantities.entrySet()) {

            Product product = entry.getKey();

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidSalesOperationException(
                        "Cannot complete sales because product is inactive: "
                                + product.getProductName()
                );
            }

            int requiredQuantity = entry.getValue();

            int availableQuantity = product.getQuantity() == null
                    ? 0
                    : product.getQuantity();

            if (availableQuantity < requiredQuantity) {
                throw new InvalidSalesOperationException(
                        "Insufficient stock for product: "
                                + product.getProductName()
                                + ". Available: "
                                + availableQuantity
                                + ", Required: "
                                + requiredQuantity
                );
            }
        }

        for (Map.Entry<Product, Integer> entry : productQuantities.entrySet()) {

            inventoryService.decreaseStock(
                    entry.getKey(),
                    entry.getValue(),
                    StockMovementType.SALE,
                    sales.getSalesNumber(),
                    "Sales completed"
            );
        }

        sales.setStatus(SalesStatus.COMPLETED);

        Sales savedSales = salesRepository.save(sales);

        salesInvoiceService.createInvoiceFromSales(savedSales.getId());

        return convertToResponse(savedSales);
    }

    public SalesResponse cancelSales(Long id) {

        Sales sales = findSalesByIdForUpdate(id);

        if (sales.getStatus() != SalesStatus.DRAFT) {
            throw new InvalidSalesOperationException(
                    "Only DRAFT sales can be cancelled"
            );
        }

        sales.setStatus(SalesStatus.CANCELLED);

        return convertToResponse(salesRepository.save(sales));
    }

    public SalesResponse updateSales(Long id, SalesRequest request) {

        Sales sales = findSalesByIdForUpdate(id);

        if (sales.getStatus() != SalesStatus.DRAFT) {
            throw new InvalidSalesOperationException(
                    "Only DRAFT sales can be updated"
            );
        }

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found with ID: " + request.getCustomerId()
                ));

        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw new InvalidSalesOperationException(
                    "Cannot update sales with an inactive customer"
            );
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesItem> newItems = new ArrayList<>();

        for (SalesItemRequest itemRequest : request.getItems()) {

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with ID: " + itemRequest.getProductId()
                    ));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidSalesOperationException(
                        "Cannot sell an inactive product: "
                                + product.getProductName()
                );
            }

            SalesItem item = new SalesItem();
            item.setSales(sales);
            item.setProduct(product);
            item.setProductCodeSnapshot(product.getProductCode());
            item.setProductNameSnapshot(product.getProductName());
            item.setQuantity(itemRequest.getQuantity());

            BigDecimal unitPrice = product.getSellingPrice();
            item.setUnitPrice(unitPrice);

            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            item.setSubtotal(subtotal);
            newItems.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        sales.setCustomer(customer);
        sales.getItems().clear();
        sales.getItems().addAll(newItems);
        sales.setTotalAmount(totalAmount);

        return convertToResponse(salesRepository.save(sales));
    }

    private Sales findSalesById(Long id) {

        return salesRepository.findById(id)
                .orElseThrow(() -> new SalesNotFoundException(
                        "Sales not found with ID: " + id
                ));
    }

    private Sales findSalesByIdForUpdate(Long id) {

        return salesRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new SalesNotFoundException(
                        "Sales not found with ID: " + id
                ));
    }

    private SalesResponse convertToResponse(Sales sales) {

        SalesResponse response = new SalesResponse();

        response.setId(sales.getId());
        response.setSalesNumber(sales.getSalesNumber());
        response.setCustomerId(sales.getCustomer().getId());
        response.setCustomerName(sales.getCustomer().getCustomerName());
        response.setSalesDate(sales.getSalesDate());
        response.setTotalAmount(sales.getTotalAmount());
        response.setStatus(sales.getStatus());

        List<SalesItemResponse> items = sales.getItems()
                .stream()
                .map(item -> {

                    SalesItemResponse itemResponse = new SalesItemResponse();

                    itemResponse.setId(item.getId());
                    itemResponse.setProductId(item.getProduct().getId());

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

                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setUnitPrice(item.getUnitPrice());
                    itemResponse.setSubtotal(item.getSubtotal());

                    return itemResponse;
                })
                .toList();

        response.setItems(items);

        return response;
    }
}