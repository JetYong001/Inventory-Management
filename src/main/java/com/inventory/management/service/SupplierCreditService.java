
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierCreditService {

    private final SupplierRepository supplierRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final SupplierCreditTransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    public SupplierCreditService(
            SupplierRepository supplierRepository,
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            SupplierCreditTransactionRepository transactionRepository,
            PaymentRepository paymentRepository) {

        this.supplierRepository = supplierRepository;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.transactionRepository = transactionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public SupplierCreditBalanceResponse getSupplierCreditBalance(Long supplierId) {

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found"));

        List<SupplierCreditTransaction> transactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplierId
                );

        BigDecimal totalIssued = calculateTotal(
                transactions,
                SupplierCreditTransactionType.CREDIT_NOTE_ISSUED
        );

        BigDecimal totalApplied = calculateNetAppliedCredit(transactions);

        BigDecimal totalRefunded = calculateTotal(
                transactions,
                SupplierCreditTransactionType.REFUND
        );

        BigDecimal availableBalance = calculateAvailableBalance(transactions);

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

    @Transactional
    public SupplierCreditApplicationResponse applySupplierCredit(
            SupplierCreditApplicationRequest request) {

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Application amount must be greater than zero"
            );
        }

        Supplier supplier = supplierRepository
                .findByIdForUpdate(request.getSupplierId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found"));

        PurchaseInvoice invoice = purchaseInvoiceRepository
                .findByIdForUpdate(request.getPurchaseInvoiceId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Purchase invoice not found"));

        if (invoice.getPurchase() == null
                || invoice.getPurchase().getSupplier() == null
                || !invoice.getPurchase().getSupplier().getId()
                .equals(supplier.getId())) {

            throw new IllegalArgumentException(
                    "Purchase invoice does not belong to this supplier"
            );
        }

        List<SupplierCreditTransaction> supplierTransactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplier.getId()
                );

        BigDecimal availableBalance =
                calculateAvailableBalance(supplierTransactions);

        BigDecimal amount = request.getAmount();

        if (amount.compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient supplier credit balance"
            );
        }

        BigDecimal paidAmount = paymentRepository
                .getTotalPurchaseInvoicePayments(invoice.getId());

        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }

        List<SupplierCreditTransaction> invoiceTransactions =
                transactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(
                                invoice.getId()
                        );

        BigDecimal netAppliedCredit =
                calculateNetAppliedCredit(invoiceTransactions);

        BigDecimal outstandingBalance = invoice.getTotalAmount()
                .subtract(paidAmount)
                .subtract(netAppliedCredit);

        if (outstandingBalance.compareTo(BigDecimal.ZERO) < 0) {
            outstandingBalance = BigDecimal.ZERO;
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
        transaction.setReferenceNumber(
                "SCA-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 12)
                        .toUpperCase()
        );
        transaction.setRemarks(request.getRemarks());

        transaction = transactionRepository.saveAndFlush(transaction);

        BigDecimal updatedAppliedCredit =
                netAppliedCredit.add(amount);

        BigDecimal updatedOutstanding =
                outstandingBalance.subtract(amount);

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

    @Transactional
    public SupplierCreditReversalResponse reverseSupplierCreditApplication(
            Long transactionId,
            SupplierCreditReversalRequest request) {

        SupplierCreditTransaction originalTransaction =
                transactionRepository.findById(transactionId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Supplier credit transaction not found"
                                ));

        if (originalTransaction.getTransactionType()
                != SupplierCreditTransactionType.CREDIT_APPLIED) {

            throw new IllegalArgumentException(
                    "Only CREDIT_APPLIED transactions can be reversed"
            );
        }

        if (transactionRepository.existsByReversedTransaction_Id(transactionId)) {
            throw new IllegalArgumentException(
                    "This credit application has already been reversed"
            );
        }

        if (originalTransaction.getPurchaseInvoice() == null
                || originalTransaction.getSupplier() == null) {

            throw new IllegalArgumentException(
                    "Original credit application has invalid supplier or invoice"
            );
        }

        Supplier supplier = supplierRepository
                .findByIdForUpdate(originalTransaction.getSupplier().getId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found"));

        PurchaseInvoice invoice = purchaseInvoiceRepository
                .findByIdForUpdate(
                        originalTransaction.getPurchaseInvoice().getId()
                )
                .orElseThrow(() ->
                        new EntityNotFoundException("Purchase invoice not found"));

        if (invoice.getPurchase() == null
                || invoice.getPurchase().getSupplier() == null
                || !invoice.getPurchase().getSupplier().getId()
                .equals(supplier.getId())) {

            throw new IllegalArgumentException(
                    "Original transaction supplier does not match invoice supplier"
            );
        }

        BigDecimal reversedAmount =
                originalTransaction.getAmount().abs();

        if (reversedAmount.compareTo(BigDecimal.ZERO) <= 0) {
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

        BigDecimal paidAmount = paymentRepository
                .getTotalPurchaseInvoicePayments(invoice.getId());

        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }

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

        BigDecimal updatedOutstanding = invoice.getTotalAmount()
                .subtract(paidAmount)
                .subtract(updatedAppliedCredit);

        if (updatedOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            updatedOutstanding = BigDecimal.ZERO;
        }

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
        reversalTransaction.setRemarks(request.getRemarks());
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

    private BigDecimal calculateAvailableBalance(
            List<SupplierCreditTransaction> transactions) {

        return transactions.stream()
                .map(SupplierCreditTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotal(
            List<SupplierCreditTransaction> transactions,
            SupplierCreditTransactionType type) {

        return transactions.stream()
                .filter(t -> t.getTransactionType() == type)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateNetAppliedCredit(
            List<SupplierCreditTransaction> transactions) {

        BigDecimal applied = calculateTotal(
                transactions,
                SupplierCreditTransactionType.CREDIT_APPLIED
        );

        BigDecimal reversed = calculateTotal(
                transactions,
                SupplierCreditTransactionType.CREDIT_APPLICATION_REVERSED
        );

        return applied.subtract(reversed);
    }

    private void updateInvoiceStatus(
            PurchaseInvoice invoice,
            BigDecimal paidAmount,
            BigDecimal appliedCredit,
            BigDecimal outstandingBalance) {

        BigDecimal totalAmount = invoice.getTotalAmount();

        if (outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {

            if (paidAmount.compareTo(totalAmount) >= 0) {
                invoice.setStatus(PurchaseInvoiceStatus.PAID);

            } else if (appliedCredit.compareTo(totalAmount) >= 0
                    && paidAmount.compareTo(BigDecimal.ZERO) == 0) {
                invoice.setStatus(PurchaseInvoiceStatus.CREDITED);

            } else {
                invoice.setStatus(PurchaseInvoiceStatus.SETTLED);
            }

        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0
                && appliedCredit.compareTo(BigDecimal.ZERO) > 0) {

            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_SETTLED);

        } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_PAID);

        } else if (appliedCredit.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_CREDITED);

        } else {
            invoice.setStatus(PurchaseInvoiceStatus.UNPAID);
        }
    }

    private SupplierCreditTransactionResponse convertToTransactionResponse(
            SupplierCreditTransaction transaction) {

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
