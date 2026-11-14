package com.inventory.management.service;

import com.inventory.management.dto.request.SupplierCreditApplicationRequest;
import com.inventory.management.dto.request.SupplierCreditReversalRequest;

import com.inventory.management.dto.response.SupplierCreditApplicationResponse;
import com.inventory.management.dto.response.SupplierCreditBalanceResponse;
import com.inventory.management.dto.response.SupplierCreditReversalResponse;
import com.inventory.management.dto.response.SupplierCreditTransactionResponse;

import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.entity.Supplier;
import com.inventory.management.entity.SupplierCreditTransaction;

import com.inventory.management.enums.PurchaseInvoiceStatus;
import com.inventory.management.enums.SupplierCreditTransactionType;

import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.PurchaseInvoiceRepository;
import com.inventory.management.repository.SupplierCreditTransactionRepository;
import com.inventory.management.repository.SupplierRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierCreditService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SupplierRepository supplierRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final SupplierCreditTransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    public SupplierCreditService(
            SupplierRepository supplierRepository,
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            SupplierCreditTransactionRepository transactionRepository,
            PaymentRepository paymentRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.transactionRepository = transactionRepository;
        this.paymentRepository = paymentRepository;
    }

    // ========================================
    // SUPPLIER CREDIT BALANCE
    // ========================================

    @Transactional(readOnly = true)
    public SupplierCreditBalanceResponse getSupplierCreditBalance(
            Long supplierId
    ) {
        if (supplierId == null || supplierId <= 0) {
            throw new IllegalArgumentException(
                    "Valid supplier ID is required"
            );
        }

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found")
                );

        List<SupplierCreditTransaction> transactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplierId
                );

        BigDecimal totalIssued = calculateTotal(
                transactions,
                SupplierCreditTransactionType.CREDIT_NOTE_ISSUED
        );

        BigDecimal totalApplied =
                calculateNetAppliedCredit(transactions);

        BigDecimal totalRefunded = calculateTotal(
                transactions,
                SupplierCreditTransactionType.REFUND
        );

        BigDecimal availableBalance =
                calculateAvailableBalance(transactions);

        SupplierCreditBalanceResponse response =
                new SupplierCreditBalanceResponse();

        response.setSupplierId(supplier.getId());
        response.setSupplierCode(supplier.getSupplierCode());
        response.setSupplierName(supplier.getSupplierName());

        response.setTotalCreditIssued(totalIssued);
        response.setTotalCreditApplied(totalApplied);
        response.setTotalRefunded(totalRefunded);
        response.setAvailableCreditBalance(availableBalance);

        response.setTransactions(
                transactions.stream()
                        .map(this::convertToTransactionResponse)
                        .toList()
        );

        return response;
    }

    // ========================================
    // APPLY SUPPLIER CREDIT
    // ========================================

    @Transactional
    public SupplierCreditApplicationResponse applySupplierCredit(
            SupplierCreditApplicationRequest request,
            String rawIdempotencyKey
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Supplier credit application request is required"
            );
        }

        if (request.getSupplierId() == null
                || request.getSupplierId() <= 0
                || request.getPurchaseInvoiceId() == null
                || request.getPurchaseInvoiceId() <= 0) {
            throw new IllegalArgumentException(
                    "Valid supplier and purchase invoice IDs are required"
            );
        }

        String idempotencyKey =
                normalizeIdempotencyKey(rawIdempotencyKey);

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Application amount must be greater than zero"
            );
        }

        if (amount.scale() > 2
                || amount.precision() - amount.scale() > 10) {
            throw new IllegalArgumentException(
                    "Application amount supports up to 10 integer digits "
                            + "and 2 decimal places"
            );
        }

        Supplier supplier = supplierRepository
                .findByIdForUpdate(request.getSupplierId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found")
                );

        // Check after locking the supplier to avoid duplicate applications.
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This credit application request has already been processed"
            );
        }

        PurchaseInvoice invoice = purchaseInvoiceRepository
                .findByIdForUpdate(request.getPurchaseInvoiceId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Purchase invoice not found")
                );

        validateInvoiceOwnership(invoice, supplier.getId());

        List<SupplierCreditTransaction> supplierTransactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplier.getId()
                );

        BigDecimal availableBalance =
                calculateAvailableBalance(supplierTransactions);

        if (amount.compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient supplier credit balance. Available: "
                            + availableBalance
            );
        }

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalPurchaseInvoicePayments(
                        invoice.getId()
                )
        );

        List<SupplierCreditTransaction> invoiceTransactions =
                transactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(
                                invoice.getId()
                        );

        BigDecimal netAppliedCredit =
                calculateNetAppliedCredit(invoiceTransactions);

        BigDecimal outstandingBalance = calculateOutstanding(
                invoice.getTotalAmount(),
                paidAmount,
                netAppliedCredit
        );

        if (outstandingBalance.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "This purchase invoice has no outstanding balance"
            );
        }

        if (amount.compareTo(outstandingBalance) > 0) {
            throw new IllegalArgumentException(
                    "Application amount exceeds invoice outstanding balance"
            );
        }

        SupplierCreditTransaction transaction =
                new SupplierCreditTransaction();

        transaction.setSupplier(supplier);
        transaction.setPurchaseInvoice(invoice);

        transaction.setTransactionType(
                SupplierCreditTransactionType.CREDIT_APPLIED
        );

        transaction.setAmount(amount.negate());
        transaction.setIdempotencyKey(idempotencyKey);

        transaction.setReferenceNumber(
                "SCA-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 12)
                        .toUpperCase()
        );

        transaction.setRemarks(
                request.getRemarks() == null
                        ? null
                        : request.getRemarks().trim()
        );

        try {
            transaction =
                    transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This credit application request has already been processed",
                    exception
            );
        }

        BigDecimal updatedAppliedCredit =
                netAppliedCredit.add(amount);

        BigDecimal updatedOutstanding =
                outstandingBalance.subtract(amount).max(ZERO);

        updateInvoiceStatus(
                invoice,
                paidAmount,
                updatedAppliedCredit,
                updatedOutstanding
        );

        purchaseInvoiceRepository.save(invoice);

        SupplierCreditApplicationResponse response =
                new SupplierCreditApplicationResponse();

        response.setTransactionId(transaction.getId());
        response.setSupplierId(supplier.getId());
        response.setPurchaseInvoiceId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());
        response.setAppliedAmount(amount);

        response.setAvailableCreditBalance(
                availableBalance.subtract(amount)
        );

        response.setOutstandingBalance(updatedOutstanding);
        response.setInvoiceStatus(invoice.getStatus());
        response.setCreatedAt(transaction.getCreatedAt());

        return response;
    }

    // ========================================
    // REVERSE SUPPLIER CREDIT
    // ========================================

    @Transactional
    public SupplierCreditReversalResponse reverseSupplierCreditApplication(
            Long transactionId,
            SupplierCreditReversalRequest request
    ) {
        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException(
                    "Valid supplier credit transaction ID is required"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Supplier credit reversal request is required"
            );
        }

        SupplierCreditTransaction originalTransaction =
                transactionRepository.findByIdForUpdate(transactionId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Supplier credit transaction not found"
                                )
                        );

        if (originalTransaction.getTransactionType()
                != SupplierCreditTransactionType.CREDIT_APPLIED
                || originalTransaction.getAmount() == null
                || originalTransaction.getAmount().compareTo(ZERO) >= 0) {
            throw new IllegalArgumentException(
                    "Only negative CREDIT_APPLIED transactions can be reversed"
            );
        }

        if (originalTransaction.getSupplier() == null
                || originalTransaction.getPurchaseInvoice() == null) {
            throw new IllegalArgumentException(
                    "Original credit application has invalid supplier or invoice"
            );
        }

        // Check after locking the original transaction.
        if (transactionRepository.existsByReversedTransaction_Id(
                transactionId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This credit application has already been reversed"
            );
        }

        Supplier supplier = supplierRepository
                .findByIdForUpdate(
                        originalTransaction.getSupplier().getId()
                )
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found")
                );

        PurchaseInvoice invoice = purchaseInvoiceRepository
                .findByIdForUpdate(
                        originalTransaction.getPurchaseInvoice().getId()
                )
                .orElseThrow(() ->
                        new EntityNotFoundException("Purchase invoice not found")
                );

        validateInvoiceOwnership(invoice, supplier.getId());

        BigDecimal reversedAmount =
                originalTransaction.getAmount().abs();

        if (reversedAmount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Original application amount must be greater than zero"
            );
        }

        List<SupplierCreditTransaction> supplierTransactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplier.getId()
                );

        BigDecimal availableBalance =
                calculateAvailableBalance(supplierTransactions);

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalPurchaseInvoicePayments(
                        invoice.getId()
                )
        );

        List<SupplierCreditTransaction> invoiceTransactions =
                transactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(
                                invoice.getId()
                        );

        BigDecimal netAppliedCredit =
                calculateNetAppliedCredit(invoiceTransactions);

        if (netAppliedCredit.compareTo(reversedAmount) < 0) {
            throw new IllegalArgumentException(
                    "Net applied credit is insufficient for this reversal"
            );
        }

        BigDecimal updatedAppliedCredit =
                netAppliedCredit.subtract(reversedAmount);

        BigDecimal updatedOutstanding = calculateOutstanding(
                invoice.getTotalAmount(),
                paidAmount,
                updatedAppliedCredit
        );

        SupplierCreditTransaction reversalTransaction =
                new SupplierCreditTransaction();

        reversalTransaction.setSupplier(supplier);
        reversalTransaction.setPurchaseInvoice(invoice);

        reversalTransaction.setTransactionType(
                SupplierCreditTransactionType.CREDIT_APPLICATION_REVERSED
        );

        reversalTransaction.setAmount(reversedAmount);

        reversalTransaction.setReferenceNumber(
                "SCR-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 12)
                        .toUpperCase()
        );

        reversalTransaction.setRemarks(
                request.getRemarks() == null
                        ? "Supplier credit application reversed"
                        : request.getRemarks().trim()
        );

        reversalTransaction.setReversedTransaction(originalTransaction);

        reversalTransaction =
                transactionRepository.saveAndFlush(reversalTransaction);

        updateInvoiceStatus(
                invoice,
                paidAmount,
                updatedAppliedCredit,
                updatedOutstanding
        );

        purchaseInvoiceRepository.save(invoice);

        SupplierCreditReversalResponse response =
                new SupplierCreditReversalResponse();

        response.setReversalTransactionId(reversalTransaction.getId());
        response.setReversalReferenceNumber(
                reversalTransaction.getReferenceNumber()
        );

        response.setOriginalTransactionId(originalTransaction.getId());
        response.setOriginalReferenceNumber(
                originalTransaction.getReferenceNumber()
        );

        response.setSupplierId(supplier.getId());
        response.setPurchaseInvoiceId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());

        response.setReversedAmount(reversedAmount);
        response.setAvailableCreditBalance(
                availableBalance.add(reversedAmount)
        );

        response.setOutstandingBalance(updatedOutstanding);
        response.setInvoiceStatus(invoice.getStatus());
        response.setCreatedAt(reversalTransaction.getCreatedAt());

        return response;
    }

    // ========================================
    // VALIDATION
    // ========================================

    private String normalizeIdempotencyKey(String rawKey) {

        if (rawKey == null || rawKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required"
            );
        }

        String key = rawKey.trim();

        if (key.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key cannot exceed 100 characters"
            );
        }

        return key;
    }

    private void validateInvoiceOwnership(
            PurchaseInvoice invoice,
            Long supplierId
    ) {
        if (invoice.getPurchase() == null
                || invoice.getPurchase().getSupplier() == null
                || invoice.getPurchase().getSupplier().getId() == null
                || !supplierId.equals(
                invoice.getPurchase().getSupplier().getId()
        )) {
            throw new IllegalArgumentException(
                    "Purchase invoice does not belong to this supplier"
            );
        }
    }

    // ========================================
    // CREDIT CALCULATIONS
    // ========================================

    private BigDecimal calculateAvailableBalance(
            List<SupplierCreditTransaction> transactions
    ) {
        return transactions.stream()
                .map(SupplierCreditTransaction::getAmount)
                .map(this::safeAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotal(
            List<SupplierCreditTransaction> transactions,
            SupplierCreditTransactionType type
    ) {
        return transactions.stream()
                .filter(transaction ->
                        transaction.getTransactionType() == type
                )
                .map(SupplierCreditTransaction::getAmount)
                .map(this::safeAmount)
                .map(BigDecimal::abs)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal calculateNetAppliedCredit(
            List<SupplierCreditTransaction> transactions
    ) {
        BigDecimal applied = calculateTotal(
                transactions,
                SupplierCreditTransactionType.CREDIT_APPLIED
        );

        BigDecimal reversed = calculateTotal(
                transactions,
                SupplierCreditTransactionType.CREDIT_APPLICATION_REVERSED
        );

        return applied.subtract(reversed).max(ZERO);
    }

    private BigDecimal calculateOutstanding(
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal appliedCredit
    ) {
        return safeAmount(totalAmount)
                .subtract(safeAmount(paidAmount))
                .subtract(safeAmount(appliedCredit))
                .max(ZERO);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    // ========================================
    // INVOICE STATUS
    // ========================================

    private void updateInvoiceStatus(
            PurchaseInvoice invoice,
            BigDecimal paidAmount,
            BigDecimal appliedCredit,
            BigDecimal outstandingBalance
    ) {
        BigDecimal totalAmount = safeAmount(invoice.getTotalAmount());

        if (outstandingBalance.compareTo(ZERO) == 0) {

            if (paidAmount.compareTo(totalAmount) >= 0) {
                invoice.setStatus(PurchaseInvoiceStatus.PAID);

            } else if (appliedCredit.compareTo(totalAmount) >= 0
                    && paidAmount.compareTo(ZERO) == 0) {
                invoice.setStatus(PurchaseInvoiceStatus.CREDITED);

            } else {
                invoice.setStatus(PurchaseInvoiceStatus.SETTLED);
            }

        } else if (paidAmount.compareTo(ZERO) > 0
                && appliedCredit.compareTo(ZERO) > 0) {

            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_SETTLED);

        } else if (paidAmount.compareTo(ZERO) > 0) {

            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_PAID);

        } else if (appliedCredit.compareTo(ZERO) > 0) {

            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_CREDITED);

        } else {

            invoice.setStatus(PurchaseInvoiceStatus.UNPAID);
        }
    }

    // ========================================
    // RESPONSE MAPPING
    // ========================================

    private SupplierCreditTransactionResponse convertToTransactionResponse(
            SupplierCreditTransaction transaction
    ) {
        SupplierCreditTransactionResponse response =
                new SupplierCreditTransactionResponse();

        response.setId(transaction.getId());
        response.setTransactionType(transaction.getTransactionType());
        response.setAmount(transaction.getAmount());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setRemarks(transaction.getRemarks());
        response.setCreatedAt(transaction.getCreatedAt());

        if (transaction.getPurchaseCreditNote() != null) {
            response.setPurchaseCreditNoteId(
                    transaction.getPurchaseCreditNote().getId()
            );

            response.setCreditNoteNumber(
                    transaction.getPurchaseCreditNote().getCreditNoteNumber()
            );
        }

        if (transaction.getPurchaseInvoice() != null) {
            response.setPurchaseInvoiceId(
                    transaction.getPurchaseInvoice().getId()
            );

            response.setInvoiceNumber(
                    transaction.getPurchaseInvoice().getInvoiceNumber()
            );
        }

        return response;
    }
}