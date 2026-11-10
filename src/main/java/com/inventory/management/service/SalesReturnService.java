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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        this.customerCreditTransactionRepository = customerCreditTransactionRepository;
        this.inventoryService = inventoryService;
    }

    public SalesReturnResponse createSalesReturn(
            SalesReturnRequest request
    ) {

        validateRequest(request);

        Sales sales = salesRepository.findByIdForUpdate(
                request.getSalesId()
        ).orElseThrow(() ->
                new IllegalArgumentException("Sales not found")
        );

        if (sales.getStatus() != SalesStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "Only completed sales can be returned"
            );
        }

        if (sales.getCustomer() == null) {
            throw new IllegalStateException(
                    "Sales customer information is missing"
            );
        }

        Customer customer = customerRepository.findByIdForUpdate(
                sales.getCustomer().getId()
        ).orElseThrow(() ->
                new IllegalArgumentException("Customer not found")
        );

        SalesInvoice foundInvoice = salesInvoiceRepository
                .findBySales_Id(sales.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Sales invoice not found")
                );

        SalesInvoice salesInvoice = salesInvoiceRepository
                .findByIdForUpdate(foundInvoice.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Sales invoice not found")
                );

        Set<Long> processedSalesItemIds = new HashSet<>();

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setReturnNumber(generateReturnNumber());
        salesReturn.setSales(sales);
        salesReturn.setSalesInvoice(salesInvoice);
        salesReturn.setReturnDate(LocalDateTime.now());
        salesReturn.setReason(request.getReason().trim());

        BigDecimal totalAmount = ZERO;

        for (SalesReturnItemRequest itemRequest : request.getItems()) {

            if (itemRequest == null) {
                throw new IllegalArgumentException(
                        "Sales return item cannot be null"
                );
            }

            Long salesItemId = itemRequest.getSalesItemId();
            Integer returnQuantity = itemRequest.getQuantity();

            if (salesItemId == null || salesItemId <= 0) {
                throw new IllegalArgumentException(
                        "Valid sales item ID is required"
                );
            }

            if (returnQuantity == null || returnQuantity <= 0) {
                throw new IllegalArgumentException(
                        "Return quantity must be greater than zero"
                );
            }

            if (!processedSalesItemIds.add(salesItemId)) {
                throw new IllegalArgumentException(
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
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Sales item does not belong to this sales order"
                    ));

            if (salesItem.getProduct() == null) {
                throw new IllegalStateException(
                        "Product is missing from sales item: " + salesItemId
                );
            }

            if (salesItem.getQuantity() == null
                    || salesItem.getQuantity() <= 0) {
                throw new IllegalStateException(
                        "Original sales item quantity is invalid"
                );
            }

            Long previouslyReturned =
                    salesReturnItemRepository.getReturnedQuantity(salesItemId);

            long returnedQuantity =
                    previouslyReturned == null ? 0 : previouslyReturned;

            long requestedTotal =
                    returnedQuantity + returnQuantity;

            if (requestedTotal > salesItem.getQuantity()) {
                throw new IllegalArgumentException(
                        "Return quantity exceeds original sales quantity for "
                                + salesItem.getProductNameSnapshot()
                );
            }

            if (salesItem.getUnitPrice() == null
                    || salesItem.getUnitPrice().compareTo(ZERO) < 0) {
                throw new IllegalStateException(
                        "Original sales item unit price is invalid"
                );
            }

            BigDecimal unitPrice = salesItem.getUnitPrice();

            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(returnQuantity)
            );

            SalesReturnItem returnItem = new SalesReturnItem();

            returnItem.setSalesReturn(salesReturn);
            returnItem.setSalesItem(salesItem);
            returnItem.setProductCode(salesItem.getProductCodeSnapshot());
            returnItem.setProductName(salesItem.getProductNameSnapshot());
            returnItem.setQuantity(returnQuantity);
            returnItem.setUnitPrice(unitPrice);
            returnItem.setSubtotal(subtotal);

            salesReturn.getItems().add(returnItem);

            inventoryService.increaseStock(
                    salesItem.getProduct(),
                    returnQuantity,
                    StockMovementType.SALES_RETURN,
                    salesReturn.getReturnNumber(),
                    "Sales return"
            );

            totalAmount = totalAmount.add(subtotal);
        }

        salesReturn.setTotalAmount(totalAmount);

        SalesReturn savedReturn =
                salesReturnRepository.saveAndFlush(salesReturn);

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

        BigDecimal currentApplied = totalAmount.min(remainingUnpaid);

        SalesCreditNote creditNote = new SalesCreditNote();

        creditNote.setCreditNoteNumber(generateCreditNoteNumber());
        creditNote.setSalesReturn(savedReturn);
        creditNote.setSalesInvoice(salesInvoice);
        creditNote.setTotalAmount(totalAmount);
        creditNote.setStatus(CreditNoteStatus.ISSUED);

        SalesCreditNote savedCreditNote =
                salesCreditNoteRepository.saveAndFlush(creditNote);

        createCreditTransaction(
                customer,
                savedCreditNote,
                salesInvoice,
                CustomerCreditTransactionType.CREDIT_NOTE_ISSUED,
                totalAmount,
                savedCreditNote.getCreditNoteNumber(),
                "Sales credit note issued"
        );

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

        updateSalesInvoiceStatus(salesInvoice);

        return convertToResponse(savedReturn);
    }

    private void validateRequest(SalesReturnRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Sales return request is required"
            );
        }

        if (request.getSalesId() == null || request.getSalesId() <= 0) {
            throw new IllegalArgumentException(
                    "Valid sales ID is required"
            );
        }

        if (request.getReason() == null
                || request.getReason().isBlank()) {
            throw new IllegalArgumentException(
                    "Return reason is required"
            );
        }

        if (request.getReason().length() > 500) {
            throw new IllegalArgumentException(
                    "Return reason cannot exceed 500 characters"
            );
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one return item is required"
            );
        }
    }

    private BigDecimal getNetAppliedCredit(Long invoiceId) {

        BigDecimal amount =
                customerCreditTransactionRepository
                        .getNetAppliedCreditForSalesInvoice(
                                invoiceId,
                                APPLIED_TYPES
                        );

        return safeAmount(amount).max(ZERO);
    }

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

    private void updateSalesInvoiceStatus(SalesInvoice invoice) {

        BigDecimal total = safeAmount(invoice.getTotalAmount());

        BigDecimal paid = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(
                        invoice.getId()
                )
        );

        BigDecimal appliedCredit = getNetAppliedCredit(invoice.getId());

        BigDecimal remaining = total
                .subtract(paid)
                .subtract(appliedCredit)
                .max(ZERO);

        invoice.setStatus(
                determineStatus(total, paid, appliedCredit, remaining)
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

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse getSalesReturnById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Valid sales return ID is required"
            );
        }

        SalesReturn salesReturn = salesReturnRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Sales return not found")
                );

        return convertToResponse(salesReturn);
    }

    @Transactional(readOnly = true)
    public List<SalesReturnResponse> getSalesReturnsBySalesId(Long salesId) {

        if (salesId == null || salesId <= 0) {
            throw new IllegalArgumentException(
                    "Valid sales ID is required"
            );
        }

        return salesReturnRepository
                .findBySales_IdOrderByReturnDateDesc(salesId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private SalesReturnResponse convertToResponse(SalesReturn salesReturn) {

        SalesReturnResponse response = new SalesReturnResponse();

        response.setId(salesReturn.getId());
        response.setReturnNumber(salesReturn.getReturnNumber());
        response.setSalesId(salesReturn.getSales().getId());
        response.setSalesNumber(salesReturn.getSales().getSalesNumber());
        response.setSalesInvoiceId(salesReturn.getSalesInvoice().getId());
        response.setInvoiceNumber(
                salesReturn.getSalesInvoice().getInvoiceNumber()
        );
        response.setReturnDate(salesReturn.getReturnDate());
        response.setReason(salesReturn.getReason());
        response.setTotalAmount(salesReturn.getTotalAmount());

        List<SalesReturnItemResponse> items = salesReturn.getItems()
                .stream()
                .map(item -> {

                    SalesReturnItemResponse itemResponse =
                            new SalesReturnItemResponse();

                    itemResponse.setId(item.getId());
                    itemResponse.setSalesItemId(
                            item.getSalesItem().getId()
                    );
                    itemResponse.setProductCode(item.getProductCode());
                    itemResponse.setProductName(item.getProductName());
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setUnitPrice(item.getUnitPrice());
                    itemResponse.setSubtotal(item.getSubtotal());

                    return itemResponse;
                })
                .toList();

        response.setItems(items);

        return response;
    }

    private String generateReturnNumber() {

        return "SRT-"
                + LocalDateTime.now().getYear()
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private String generateCreditNoteNumber() {

        return "SCN-"
                + LocalDateTime.now().getYear()
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}