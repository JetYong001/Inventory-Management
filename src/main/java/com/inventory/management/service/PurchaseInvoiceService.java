
package com.inventory.management.service;

import com.inventory.management.dto.response.PurchaseInvoiceItemResponse;
import com.inventory.management.dto.response.PurchaseInvoiceResponse;

import com.inventory.management.entity.Payment;
import com.inventory.management.entity.Purchase;
import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.entity.PurchaseInvoiceItem;
import com.inventory.management.entity.PurchaseItem;
import com.inventory.management.entity.Supplier;
import com.inventory.management.entity.SupplierCreditTransaction;

import com.inventory.management.enums.PurchaseInvoiceStatus;
import com.inventory.management.enums.PurchaseStatus;
import com.inventory.management.enums.SupplierCreditTransactionType;

import com.inventory.management.exception.InvalidPurchaseOperationException;
import com.inventory.management.exception.PurchaseNotFoundException;

import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.PurchaseInvoiceRepository;
import com.inventory.management.repository.PurchaseRepository;
import com.inventory.management.repository.SupplierCreditTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentRepository paymentRepository;
    private final SupplierCreditTransactionRepository supplierCreditTransactionRepository;

    public PurchaseInvoiceService(
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            PurchaseRepository purchaseRepository,
            PaymentRepository paymentRepository,
            SupplierCreditTransactionRepository supplierCreditTransactionRepository) {

        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
        this.supplierCreditTransactionRepository = supplierCreditTransactionRepository;
    }

    public PurchaseInvoiceResponse createInvoiceFromPurchase(Long purchaseId) {

        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new PurchaseNotFoundException(
                        "Purchase not found with ID: " + purchaseId
                ));

        if (purchase.getStatus() != PurchaseStatus.RECEIVED) {
            throw new InvalidPurchaseOperationException(
                    "Only RECEIVED purchases can generate invoices"
            );
        }

        if (purchaseInvoiceRepository.existsByPurchase_Id(purchaseId)) {
            throw new InvalidPurchaseOperationException(
                    "An invoice already exists for this purchase"
            );
        }

        Supplier supplier = purchase.getSupplier();

        PurchaseInvoice invoice = new PurchaseInvoice();

        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setPurchase(purchase);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setStatus(PurchaseInvoiceStatus.UNPAID);

        invoice.setSupplierCode(supplier.getSupplierCode());
        invoice.setSupplierName(supplier.getSupplierName());
        invoice.setSupplierContactPerson(supplier.getContactPerson());
        invoice.setSupplierPhone(supplier.getPhone());
        invoice.setSupplierEmail(supplier.getEmail());
        invoice.setSupplierAddress(supplier.getAddress());

        invoice.setTotalAmount(purchase.getTotalAmount());

        for (PurchaseItem purchaseItem : purchase.getItems()) {

            PurchaseInvoiceItem invoiceItem = new PurchaseInvoiceItem();

            invoiceItem.setInvoice(invoice);

            String productCode = purchaseItem.getProductCodeSnapshot();
            String productName = purchaseItem.getProductNameSnapshot();

            if (productCode == null || productCode.isBlank()) {
                productCode = purchaseItem.getProduct().getProductCode();
            }

            if (productName == null || productName.isBlank()) {
                productName = purchaseItem.getProduct().getProductName();
            }

            invoiceItem.setProductCode(productCode);
            invoiceItem.setProductName(productName);
            invoiceItem.setQuantity(purchaseItem.getQuantity());
            invoiceItem.setUnitCost(purchaseItem.getUnitCost());
            invoiceItem.setSubtotal(purchaseItem.getSubtotal());

            invoice.getItems().add(invoiceItem);
        }

        PurchaseInvoice savedInvoice =
                purchaseInvoiceRepository.save(invoice);

        return convertToResponse(savedInvoice);
    }

    @Transactional(readOnly = true)
    public List<PurchaseInvoiceResponse> getAllInvoices() {

        return purchaseInvoiceRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse getInvoiceById(Long invoiceId) {

        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvalidPurchaseOperationException(
                        "Purchase invoice not found with ID: " + invoiceId
                ));

        return convertToResponse(invoice);
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse getInvoiceByPurchaseId(Long purchaseId) {

        PurchaseInvoice invoice =
                purchaseInvoiceRepository.findByPurchase_Id(purchaseId)
                        .orElseThrow(() -> new InvalidPurchaseOperationException(
                                "Purchase invoice not found for purchase ID: "
                                        + purchaseId
                        ));

        return convertToResponse(invoice);
    }

    private BigDecimal getTotalPaid(Long invoiceId) {

        BigDecimal totalPaid =
                paymentRepository.getTotalPurchaseInvoicePayments(invoiceId);

        return totalPaid != null ? totalPaid : BigDecimal.ZERO;
    }

    private BigDecimal getTotalCreditApplied(Long invoiceId) {

        List<SupplierCreditTransaction> transactions =
                supplierCreditTransactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(invoiceId);

        BigDecimal totalApplied = BigDecimal.ZERO;
        BigDecimal totalReversed = BigDecimal.ZERO;

        for (SupplierCreditTransaction transaction : transactions) {

            if (transaction.getAmount() == null) {
                continue;
            }

            BigDecimal amount = transaction.getAmount().abs();

            if (transaction.getTransactionType()
                    == SupplierCreditTransactionType.CREDIT_APPLIED) {

                totalApplied = totalApplied.add(amount);

            } else if (transaction.getTransactionType()
                    == SupplierCreditTransactionType.CREDIT_APPLICATION_REVERSED) {

                totalReversed = totalReversed.add(amount);
            }
        }

        BigDecimal netApplied = totalApplied.subtract(totalReversed);

        return netApplied.max(BigDecimal.ZERO);
    }

    private BigDecimal calculateOutstanding(
            BigDecimal totalAmount,
            BigDecimal totalPaid,
            BigDecimal totalCreditApplied) {

        BigDecimal outstanding = totalAmount
                .subtract(totalPaid)
                .subtract(totalCreditApplied);

        return outstanding.max(BigDecimal.ZERO);
    }

    private PurchaseInvoiceStatus calculateStatus(
            BigDecimal totalAmount,
            BigDecimal totalPaid,
            BigDecimal totalCreditApplied,
            BigDecimal outstanding) {

        boolean hasPayment = totalPaid.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = totalCreditApplied.compareTo(BigDecimal.ZERO) > 0;

        boolean fullySettled =
                outstanding.compareTo(BigDecimal.ZERO) <= 0;

        if (fullySettled) {

            if (hasPayment && hasCredit) {
                return PurchaseInvoiceStatus.SETTLED;
            }

            if (hasCredit) {
                return PurchaseInvoiceStatus.CREDITED;
            }

            return PurchaseInvoiceStatus.PAID;
        }

        if (hasPayment && hasCredit) {
            return PurchaseInvoiceStatus.PARTIALLY_SETTLED;
        }

        if (hasCredit) {
            return PurchaseInvoiceStatus.PARTIALLY_CREDITED;
        }

        if (hasPayment) {
            return PurchaseInvoiceStatus.PARTIALLY_PAID;
        }

        return PurchaseInvoiceStatus.UNPAID;
    }

    private String generateInvoiceNumber() {

        String invoiceNumber;

        do {
            String randomCode = UUID.randomUUID()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase();

            invoiceNumber = "PINV-"
                    + LocalDate.now().getYear()
                    + "-"
                    + randomCode;

        } while (purchaseInvoiceRepository.existsByInvoiceNumber(invoiceNumber));

        return invoiceNumber;
    }

    private PurchaseInvoiceResponse convertToResponse(
            PurchaseInvoice invoice) {

        PurchaseInvoiceResponse response =
                new PurchaseInvoiceResponse();

        response.setId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());

        response.setPurchaseId(invoice.getPurchase().getId());
        response.setPurchaseNumber(
                invoice.getPurchase().getPurchaseNumber()
        );

        response.setInvoiceDate(invoice.getInvoiceDate());

        response.setSupplierCode(invoice.getSupplierCode());
        response.setSupplierName(invoice.getSupplierName());
        response.setSupplierContactPerson(
                invoice.getSupplierContactPerson()
        );
        response.setSupplierPhone(invoice.getSupplierPhone());
        response.setSupplierEmail(invoice.getSupplierEmail());
        response.setSupplierAddress(invoice.getSupplierAddress());

        BigDecimal totalAmount = invoice.getTotalAmount();
        BigDecimal totalPaid = getTotalPaid(invoice.getId());
        BigDecimal totalCreditApplied =
                getTotalCreditApplied(invoice.getId());

        BigDecimal outstandingAmount = calculateOutstanding(
                totalAmount,
                totalPaid,
                totalCreditApplied
        );

        PurchaseInvoiceStatus calculatedStatus = calculateStatus(
                totalAmount,
                totalPaid,
                totalCreditApplied,
                outstandingAmount
        );

        response.setTotalAmount(totalAmount);
        response.setTotalPaid(totalPaid);
        response.setTotalCreditApplied(totalCreditApplied);
        response.setOutstandingAmount(outstandingAmount);
        response.setStatus(calculatedStatus);

        List<PurchaseInvoiceItemResponse> items =
                invoice.getItems()
                        .stream()
                        .map(item -> {

                            PurchaseInvoiceItemResponse itemResponse =
                                    new PurchaseInvoiceItemResponse();

                            itemResponse.setId(item.getId());
                            itemResponse.setProductCode(
                                    item.getProductCode()
                            );
                            itemResponse.setProductName(
                                    item.getProductName()
                            );
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
