package com.inventory.management.service;

import com.inventory.management.dto.request.SalesReturnRequest;
import com.inventory.management.dto.request.SalesReturnItemRequest;
import com.inventory.management.dto.response.SalesReturnResponse;
import com.inventory.management.dto.response.SalesReturnItemResponse;

import com.inventory.management.entity.Customer;
import com.inventory.management.entity.CustomerCreditTransaction;
import com.inventory.management.entity.Sales;
import com.inventory.management.entity.SalesItem;
import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.entity.SalesReturn;
import com.inventory.management.entity.SalesReturnItem;
import com.inventory.management.entity.SalesCreditNote;

import com.inventory.management.enums.SalesStatus;
import com.inventory.management.enums.SalesInvoiceStatus;
import com.inventory.management.enums.StockMovementType;
import com.inventory.management.enums.CreditNoteStatus;
import com.inventory.management.enums.CustomerCreditTransactionType;

import com.inventory.management.repository.CustomerRepository;
import com.inventory.management.repository.SalesRepository;
import com.inventory.management.repository.SalesInvoiceRepository;
import com.inventory.management.repository.SalesReturnRepository;
import com.inventory.management.repository.SalesReturnItemRepository;
import com.inventory.management.repository.SalesCreditNoteRepository;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.CustomerCreditTransactionRepository;

import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class SalesReturnService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final List<CustomerCreditTransactionType> APPLIED_TYPES =
            List.of(
                    CustomerCreditTransactionType.CREDIT_APPLIED,
                    CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
            );

    private final CustomerRepository customerRepository;
    private final SalesRepository salesRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final SalesReturnItemRepository salesReturnItemRepository;
    private final SalesCreditNoteRepository salesCreditNoteRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerCreditTransactionRepository customerCreditTransactionRepository;
    private final InventoryService inventoryService;

    public SalesReturnService(
            CustomerRepository customerRepository,
            SalesRepository salesRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            SalesReturnRepository salesReturnRepository,
            SalesReturnItemRepository salesReturnItemRepository,
            SalesCreditNoteRepository salesCreditNoteRepository,
            PaymentRepository paymentRepository,
            CustomerCreditTransactionRepository customerCreditTransactionRepository,
            InventoryService inventoryService
    ) {
        this.customerRepository = customerRepository;
        this.salesRepository = salesRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.salesReturnItemRepository = salesReturnItemRepository;
        this.salesCreditNoteRepository = salesCreditNoteRepository;
        this.paymentRepository = paymentRepository;
        this.customerCreditTransactionRepository =
                customerCreditTransactionRepository;
        this.inventoryService = inventoryService;
    }

    // =========================
    // CREATE SALES RETURN
    // =========================

    public SalesReturnResponse createSalesReturn(
            SalesReturnRequest request,
            String idempotencyKey
    ) {

        validateRequest(request);

        String normalizedKey =
                normalizeIdempotencyKey(idempotencyKey);

        String requestHash =
                generateRequestHash(request);

        Sales sales = salesRepository.findByIdForUpdate(
                request.getSalesId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Sales not found"
        ));

        // Check whether this request has already been processed.
        // The sales row is locked first, so concurrent retries for
        // the same sales order are serialized.

        SalesReturn existingReturn =
                salesReturnRepository
                        .findByIdempotencyKey(normalizedKey)
                        .orElse(null);

        if (existingReturn != null) {

            return returnExistingOrReject(
                    existingReturn,
                    requestHash
            );
        }

        if (sales.getStatus() != SalesStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only completed sales can be returned"
            );
        }

        if (sales.getCustomer() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sales customer information is missing"
            );
        }

        Customer customer = customerRepository.findByIdForUpdate(
                sales.getCustomer().getId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Customer not found"
        ));

        SalesInvoice foundInvoice = salesInvoiceRepository
                .findBySales_Id(sales.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales invoice not found"
                ));

        SalesInvoice salesInvoice = salesInvoiceRepository
                .findByIdForUpdate(foundInvoice.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales invoice not found"
                ));

        Set<Long> processedSalesItemIds = new HashSet<>();

        SalesReturn salesReturn = new SalesReturn();

        salesReturn.setReturnNumber(
                BusinessCodeGenerator.temporary()
        );

        salesReturn.setSales(sales);
        salesReturn.setSalesInvoice(salesInvoice);

        salesReturn.setIdempotencyKey(normalizedKey);
        salesReturn.setRequestHash(requestHash);

        salesReturn.setReturnDate(LocalDateTime.now());
        salesReturn.setReason(request.getReason().trim());

        SalesReturn savedReturn =
                salesReturnRepository.saveAndFlush(salesReturn);

        savedReturn.setReturnNumber(
                BusinessCodeGenerator.format(
                        "SR",
                        savedReturn.getId()
                )
        );

        savedReturn =
                salesReturnRepository.saveAndFlush(savedReturn);

        BigDecimal totalAmount = ZERO;

        // =========================
        // PROCESS RETURN ITEMS
        // =========================

        for (SalesReturnItemRequest itemRequest : request.getItems()) {

            Long salesItemId = itemRequest.getSalesItemId();
            Integer returnQuantity = itemRequest.getQuantity();

            if (!processedSalesItemIds.add(salesItemId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate sales item ID in return request"
                );
            }

            SalesItem salesItem = sales.getItems()
                    .stream()
                    .filter(item ->
                            item.getId() != null
                                    && item.getId().equals(salesItemId)
                    )
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Sales item does not belong to this sales order"
                    ));

            if (salesItem.getProduct() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product is missing from sales item: " + salesItemId
                );
            }

            if (salesItem.getQuantity() == null
                    || salesItem.getQuantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Original sales item quantity is invalid"
                );
            }

            Long previouslyReturned =
                    salesReturnItemRepository.getReturnedQuantity(
                            salesItemId
                    );

            long returnedQuantity = previouslyReturned == null
                    ? 0
                    : previouslyReturned;

            long requestedTotal =
                    returnedQuantity + returnQuantity;

            if (requestedTotal > salesItem.getQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Return quantity exceeds original sales quantity for "
                                + salesItem.getProductNameSnapshot()
                );
            }

            if (salesItem.getUnitPrice() == null
                    || salesItem.getUnitPrice().compareTo(ZERO) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Original sales item unit price is invalid"
                );
            }

            BigDecimal unitPrice = salesItem.getUnitPrice();

            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(returnQuantity)
            );

            SalesReturnItem returnItem = new SalesReturnItem();

            returnItem.setSalesReturn(savedReturn);
            returnItem.setSalesItem(salesItem);

            returnItem.setProductCode(
                    salesItem.getProductCodeSnapshot()
            );

            returnItem.setProductName(
                    salesItem.getProductNameSnapshot()
            );

            returnItem.setQuantity(returnQuantity);
            returnItem.setUnitPrice(unitPrice);
            returnItem.setSubtotal(subtotal);

            savedReturn.getItems().add(returnItem);

            // Returned goods are added back to inventory.
            inventoryService.increaseStock(
                    salesItem.getProduct(),
                    returnQuantity,
                    StockMovementType.SALES_RETURN,
                    savedReturn.getReturnNumber(),
                    "Sales return"
            );

            totalAmount = totalAmount.add(subtotal);
        }

        savedReturn.setTotalAmount(totalAmount);

        savedReturn =
                salesReturnRepository.saveAndFlush(savedReturn);

        // =========================
        // CALCULATE INVOICE BALANCE
        // =========================

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(
                        salesInvoice.getId()
                )
        );

        BigDecimal previousApplied = getNetAppliedCredit(
                salesInvoice.getId()
        );

        BigDecimal unpaidBeforeCredit = safeAmount(
                salesInvoice.getTotalAmount()
        ).subtract(paidAmount).max(ZERO);

        BigDecimal remainingUnpaid = unpaidBeforeCredit
                .subtract(previousApplied)
                .max(ZERO);

        // Apply the return credit to the original invoice
        // only up to the remaining unpaid amount.

        BigDecimal currentApplied =
                totalAmount.min(remainingUnpaid);

        // =========================
        // CREATE SALES CREDIT NOTE
        // =========================

        SalesCreditNote creditNote = new SalesCreditNote();

        creditNote.setCreditNoteNumber(
                BusinessCodeGenerator.temporary()
        );

        creditNote.setSalesReturn(savedReturn);
        creditNote.setSalesInvoice(salesInvoice);
        creditNote.setTotalAmount(totalAmount);
        creditNote.setStatus(CreditNoteStatus.ISSUED);

        SalesCreditNote savedCreditNote =
                salesCreditNoteRepository.saveAndFlush(creditNote);

        savedCreditNote.setCreditNoteNumber(
                BusinessCodeGenerator.format(
                        "SCN",
                        savedCreditNote.getId()
                )
        );

        savedCreditNote =
                salesCreditNoteRepository.saveAndFlush(savedCreditNote);

        // =========================
        // ISSUE CUSTOMER CREDIT
        // =========================

        createCreditTransaction(
                customer,
                savedCreditNote,
                salesInvoice,
                CustomerCreditTransactionType.CREDIT_NOTE_ISSUED,
                totalAmount,
                savedCreditNote.getCreditNoteNumber(),
                "Sales credit note issued"
        );

        // =========================
        // APPLY CREDIT TO ORIGINAL INVOICE
        // =========================

        if (currentApplied.compareTo(ZERO) > 0) {

            createCreditTransaction(
                    customer,
                    savedCreditNote,
                    salesInvoice,
                    CustomerCreditTransactionType.CREDIT_APPLIED,
                    currentApplied.negate(),
                    savedCreditNote.getCreditNoteNumber(),
                    "Credit applied to original sales invoice"
            );
        }

        // =========================
        // UPDATE INVOICE STATUS
        // =========================

        updateSalesInvoiceStatus(salesInvoice);

        return convertToResponse(savedReturn);
    }

    // =========================
    // IDEMPOTENCY KEY
    // =========================

    private String normalizeIdempotencyKey(String key) {

        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required"
            );
        }

        String normalized = key.trim();

        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key cannot exceed 100 characters"
            );
        }

        return normalized;
    }

    private SalesReturnResponse returnExistingOrReject(
            SalesReturn existingReturn,
            String requestHash
    ) {

        if (!requestHash.equals(existingReturn.getRequestHash())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key has already been used with different request data"
            );
        }

        return convertToResponse(existingReturn);
    }

    private String generateRequestHash(
            SalesReturnRequest request
    ) {

        StringBuilder content = new StringBuilder();

        content.append(request.getSalesId()).append('\n');

        String reason = request.getReason().trim();

        content.append(reason.length())
                .append(':')
                .append(reason)
                .append('\n');

        request.getItems()
                .stream()
                .sorted(Comparator.comparing(
                        SalesReturnItemRequest::getSalesItemId
                ))
                .forEach(item -> content
                        .append(item.getSalesItemId())
                        .append(':')
                        .append(item.getQuantity())
                        .append('\n'));

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    content.toString().getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to generate sales return request hash",
                    exception
            );
        }
    }

    // =========================
    // VALIDATE REQUEST
    // =========================

    private void validateRequest(SalesReturnRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sales return request is required"
            );
        }

        if (request.getSalesId() == null
                || request.getSalesId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid sales ID is required"
            );
        }

        if (request.getReason() == null
                || request.getReason().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Return reason is required"
            );
        }

        if (request.getReason().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Return reason cannot exceed 500 characters"
            );
        }

        if (request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one return item is required"
            );
        }

        Set<Long> itemIds = new HashSet<>();

        for (SalesReturnItemRequest item : request.getItems()) {

            if (item == null
                    || item.getSalesItemId() == null
                    || item.getSalesItemId() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Valid sales item ID is required"
                );
            }

            if (item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Return quantity must be greater than zero"
                );
            }

            if (!itemIds.add(item.getSalesItemId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate sales item ID in return request"
                );
            }
        }
    }

    // =========================
    // GET NET APPLIED CREDIT
    // =========================

    private BigDecimal getNetAppliedCredit(Long invoiceId) {

        BigDecimal amount =
                customerCreditTransactionRepository
                        .getNetAppliedCreditForSalesInvoice(
                                invoiceId,
                                APPLIED_TYPES
                        );

        return safeAmount(amount).max(ZERO);
    }

    // =========================
    // CREATE CREDIT TRANSACTION
    // =========================

    private void createCreditTransaction(
            Customer customer,
            SalesCreditNote creditNote,
            SalesInvoice invoice,
            CustomerCreditTransactionType transactionType,
            BigDecimal amount,
            String referenceNumber,
            String remarks
    ) {

        CustomerCreditTransaction transaction =
                new CustomerCreditTransaction();

        transaction.setCustomer(customer);
        transaction.setSalesCreditNote(creditNote);
        transaction.setSalesInvoice(invoice);

        transaction.setTransactionType(transactionType);
        transaction.setAmount(amount);
        transaction.setReferenceNumber(referenceNumber);
        transaction.setRemarks(remarks);

        customerCreditTransactionRepository.save(transaction);
    }

    // =========================
    // UPDATE SALES INVOICE STATUS
    // =========================

    private void updateSalesInvoiceStatus(SalesInvoice invoice) {

        BigDecimal total = safeAmount(invoice.getTotalAmount());

        BigDecimal paid = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(
                        invoice.getId()
                )
        );

        BigDecimal appliedCredit =
                getNetAppliedCredit(invoice.getId());

        BigDecimal remaining = total
                .subtract(paid)
                .subtract(appliedCredit)
                .max(ZERO);

        invoice.setStatus(
                determineStatus(
                        total,
                        paid,
                        appliedCredit,
                        remaining
                )
        );

        salesInvoiceRepository.save(invoice);
    }

    private SalesInvoiceStatus determineStatus(
            BigDecimal total,
            BigDecimal paid,
            BigDecimal credit,
            BigDecimal remaining
    ) {

        if (remaining.compareTo(ZERO) > 0) {

            if (paid.compareTo(ZERO) > 0
                    && credit.compareTo(ZERO) > 0) {
                return SalesInvoiceStatus.PARTIALLY_SETTLED;
            }

            if (paid.compareTo(ZERO) > 0) {
                return SalesInvoiceStatus.PARTIALLY_PAID;
            }

            if (credit.compareTo(ZERO) > 0) {
                return SalesInvoiceStatus.PARTIALLY_CREDITED;
            }

            return SalesInvoiceStatus.UNPAID;
        }

        if (paid.compareTo(total) >= 0) {
            return SalesInvoiceStatus.PAID;
        }

        if (credit.compareTo(total) >= 0) {
            return SalesInvoiceStatus.CREDITED;
        }

        return SalesInvoiceStatus.SETTLED;
    }

    // =========================
    // GET SALES RETURN BY ID
    // =========================

    @Transactional(readOnly = true)
    public SalesReturnResponse getSalesReturnById(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid sales return ID is required"
            );
        }

        SalesReturn salesReturn =
                salesReturnRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Sales return not found"
                        ));

        return convertToResponse(salesReturn);
    }

    // =========================
    // GET SALES RETURNS BY SALES ID
    // =========================

    @Transactional(readOnly = true)
    public List<SalesReturnResponse> getSalesReturnsBySalesId(
            Long salesId
    ) {

        if (salesId == null || salesId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid sales ID is required"
            );
        }

        return salesReturnRepository
                .findBySales_IdOrderByReturnDateDesc(salesId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // =========================
    // RESPONSE MAPPING
    // =========================

    private SalesReturnResponse convertToResponse(
            SalesReturn salesReturn
    ) {

        SalesReturnResponse response =
                new SalesReturnResponse();

        response.setId(salesReturn.getId());
        response.setReturnNumber(salesReturn.getReturnNumber());

        response.setSalesId(
                salesReturn.getSales().getId()
        );

        response.setSalesNumber(
                salesReturn.getSales().getSalesNumber()
        );

        response.setSalesInvoiceId(
                salesReturn.getSalesInvoice().getId()
        );

        response.setInvoiceNumber(
                salesReturn.getSalesInvoice().getInvoiceNumber()
        );

        response.setReturnDate(
                salesReturn.getReturnDate()
        );

        response.setReason(
                salesReturn.getReason()
        );

        response.setTotalAmount(
                salesReturn.getTotalAmount()
        );

        List<SalesReturnItemResponse> items =
                salesReturn.getItems()
                        .stream()
                        .map(item -> {

                            SalesReturnItemResponse itemResponse =
                                    new SalesReturnItemResponse();

                            itemResponse.setId(item.getId());

                            itemResponse.setSalesItemId(
                                    item.getSalesItem().getId()
                            );

                            itemResponse.setProductCode(
                                    item.getProductCode()
                            );

                            itemResponse.setProductName(
                                    item.getProductName()
                            );

                            itemResponse.setQuantity(
                                    item.getQuantity()
                            );

                            itemResponse.setUnitPrice(
                                    item.getUnitPrice()
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

    // =========================
    // COMMON
    // =========================

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }
}