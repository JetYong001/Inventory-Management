package com.inventory.management.service;

import com.inventory.management.dto.request.CustomerCreditApplicationRequest;
import com.inventory.management.dto.request.CustomerCreditReversalRequest;

import com.inventory.management.dto.response.CustomerCreditApplicationResponse;
import com.inventory.management.dto.response.CustomerCreditBalanceResponse;
import com.inventory.management.dto.response.CustomerCreditReversalResponse;
import com.inventory.management.dto.response.CustomerCreditTransactionResponse;

import com.inventory.management.entity.Customer;
import com.inventory.management.entity.CustomerCreditTransaction;
import com.inventory.management.entity.SalesInvoice;

import com.inventory.management.enums.CustomerCreditTransactionType;
import com.inventory.management.enums.SalesInvoiceStatus;

import com.inventory.management.repository.CustomerRepository;
import com.inventory.management.repository.CustomerCreditTransactionRepository;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.SalesInvoiceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerCreditService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final List<CustomerCreditTransactionType> APPLIED_TYPES =
            List.of(
                    CustomerCreditTransactionType.CREDIT_APPLIED,
                    CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
            );

    private final CustomerRepository customerRepository;
    private final CustomerCreditTransactionRepository transactionRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final PaymentRepository paymentRepository;

    public CustomerCreditService(
            CustomerRepository customerRepository,
            CustomerCreditTransactionRepository transactionRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            PaymentRepository paymentRepository
    ) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    // =========================
    // CUSTOMER CREDIT BALANCE
    // =========================

    public CustomerCreditBalanceResponse getCustomerCreditBalance(
            Long customerId
    ) {

        validateCustomerId(customerId);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found with ID: " + customerId
                ));

        List<CustomerCreditTransaction> transactions =
                transactionRepository.findByCustomerIdOrderByCreatedAtDesc(
                        customerId
                );

        BigDecimal totalCreditIssued = sumByType(
                transactions,
                CustomerCreditTransactionType.CREDIT_NOTE_ISSUED
        );

        BigDecimal totalCreditApplied =
                calculateNetAppliedCredit(transactions);

        BigDecimal totalRefunded = sumByType(
                transactions,
                CustomerCreditTransactionType.REFUND
        );

        BigDecimal availableBalance =
                calculateAvailableBalance(transactions);

        List<CustomerCreditTransactionResponse> transactionResponses =
                transactions.stream()
                        .map(this::convertToResponse)
                        .toList();

        CustomerCreditBalanceResponse response =
                new CustomerCreditBalanceResponse();

        response.setCustomerId(customer.getId());
        response.setCustomerCode(customer.getCustomerCode());
        response.setCustomerName(customer.getCustomerName());

        response.setTotalCreditIssued(totalCreditIssued);
        response.setTotalCreditApplied(totalCreditApplied);
        response.setTotalRefunded(totalRefunded);
        response.setAvailableCreditBalance(availableBalance);

        response.setTransactions(transactionResponses);

        return response;
    }

    // =========================
    // APPLY CUSTOMER CREDIT
    // =========================

    @Transactional
    public CustomerCreditApplicationResponse applyCustomerCredit(
            CustomerCreditApplicationRequest request
    ) {

        validateApplicationRequest(request);

        Long customerId = request.getCustomerId();
        Long invoiceId = request.getSalesInvoiceId();

        BigDecimal amount = request.getAmount();

        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found with ID: " + customerId
                ));

        SalesInvoice invoice = salesInvoiceRepository
                .findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales invoice not found with ID: " + invoiceId
                ));

        validateInvoiceOwnership(invoice, customerId);

        List<CustomerCreditTransaction> customerTransactions =
                transactionRepository.findByCustomerIdOrderByCreatedAtDesc(
                        customerId
                );

        BigDecimal availableCredit =
                calculateAvailableBalance(customerTransactions);

        if (availableCredit.compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient customer credit. Available balance: "
                            + availableCredit
            );
        }

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(invoiceId)
        );

        BigDecimal appliedCredit =
                getNetAppliedCredit(invoiceId);

        BigDecimal outstanding = calculateOutstanding(
                invoice.getTotalAmount(),
                paidAmount,
                appliedCredit
        );

        if (outstanding.compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This sales invoice has no outstanding balance"
            );
        }

        if (amount.compareTo(outstanding) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit amount exceeds invoice outstanding balance. "
                            + "Outstanding balance: " + outstanding
            );
        }

        CustomerCreditTransaction transaction =
                new CustomerCreditTransaction();

        transaction.setCustomer(customer);
        transaction.setSalesInvoice(invoice);

        transaction.setTransactionType(
                CustomerCreditTransactionType.CREDIT_APPLIED
        );

        transaction.setAmount(amount.negate());

        transaction.setReferenceNumber(
                invoice.getInvoiceNumber()
        );

        transaction.setRemarks(
                request.getRemarks() != null
                        && !request.getRemarks().isBlank()
                        ? request.getRemarks().trim()
                        : "Customer credit applied to sales invoice"
        );

        CustomerCreditTransaction savedTransaction =
                transactionRepository.saveAndFlush(transaction);

        BigDecimal updatedAppliedCredit =
                appliedCredit.add(amount);

        updateInvoiceStatus(
                invoice,
                paidAmount,
                updatedAppliedCredit
        );

        salesInvoiceRepository.save(invoice);

        BigDecimal updatedOutstanding = calculateOutstanding(
                invoice.getTotalAmount(),
                paidAmount,
                updatedAppliedCredit
        );

        CustomerCreditApplicationResponse response =
                new CustomerCreditApplicationResponse();

        response.setTransactionId(savedTransaction.getId());
        response.setCustomerId(customer.getId());
        response.setSalesInvoiceId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());

        response.setAppliedAmount(amount);

        response.setAvailableCreditBalance(
                availableCredit.subtract(amount)
        );

        response.setOutstandingBalance(updatedOutstanding);
        response.setInvoiceStatus(invoice.getStatus());
        response.setCreatedAt(savedTransaction.getCreatedAt());

        return response;
    }

    // =========================
    // REVERSE CUSTOMER CREDIT
    // =========================

    @Transactional
    public CustomerCreditReversalResponse reverseCustomerCreditApplication(
            Long transactionId,
            CustomerCreditReversalRequest request
    ) {

        if (transactionId == null || transactionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer credit transaction ID is required"
            );
        }

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit reversal request is required"
            );
        }

        if (request.getRemarks() != null
                && request.getRemarks().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Remarks cannot exceed 500 characters"
            );
        }

        CustomerCreditTransaction original =
                transactionRepository.findById(transactionId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer credit transaction not found"
                        ));

        // Only a negative CREDIT_APPLIED transaction can be reversed.

        if (original.getTransactionType()
                != CustomerCreditTransactionType.CREDIT_APPLIED
                || original.getAmount() == null
                || original.getAmount().compareTo(ZERO) >= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only a negative CREDIT_APPLIED transaction can be reversed"
            );
        }

        if (original.getCustomer() == null
                || original.getSalesInvoice() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Original credit application is missing its customer or invoice"
            );
        }

        Long customerId = original.getCustomer().getId();
        Long invoiceId = original.getSalesInvoice().getId();

        // Lock customer first to prevent concurrent credit balance changes.

        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));

        SalesInvoice invoice = salesInvoiceRepository
                .findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales invoice not found"
                ));

        validateInvoiceOwnership(invoice, customerId);

        // The unique relationship and this check prevent reversing
        // the same original transaction more than once.

        if (transactionRepository.existsByReversedTransaction_Id(
                transactionId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This customer credit application has already been reversed"
            );
        }

        BigDecimal reversedAmount =
                original.getAmount().abs();

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(invoiceId)
        );

        BigDecimal appliedCredit =
                getNetAppliedCredit(invoiceId);

        if (appliedCredit.compareTo(reversedAmount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Net applied customer credit is insufficient for this reversal"
            );
        }

        List<CustomerCreditTransaction> customerTransactions =
                transactionRepository.findByCustomerIdOrderByCreatedAtDesc(
                        customerId
                );

        BigDecimal availableBefore =
                calculateAvailableBalance(customerTransactions);

        BigDecimal updatedAppliedCredit =
                appliedCredit.subtract(reversedAmount);

        BigDecimal updatedOutstanding = calculateOutstanding(
                invoice.getTotalAmount(),
                paidAmount,
                updatedAppliedCredit
        );

        CustomerCreditTransaction reversal =
                new CustomerCreditTransaction();

        reversal.setCustomer(customer);
        reversal.setSalesInvoice(invoice);

        // Preserve the source credit note relationship when the
        // original application came from a Sales Credit Note.

        reversal.setSalesCreditNote(
                original.getSalesCreditNote()
        );

        reversal.setTransactionType(
                CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
        );

        // Original application is negative.
        // Reversal is positive, restoring the available credit.

        reversal.setAmount(reversedAmount);

        reversal.setReferenceNumber(
                "CCR-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 12)
                        .toUpperCase()
        );

        reversal.setRemarks(
                request.getRemarks() == null
                        || request.getRemarks().isBlank()
                        ? "Customer credit application reversed"
                        : request.getRemarks().trim()
        );

        reversal.setReversedTransaction(original);

        reversal = transactionRepository.saveAndFlush(reversal);

        updateInvoiceStatus(
                invoice,
                paidAmount,
                updatedAppliedCredit
        );

        salesInvoiceRepository.save(invoice);

        CustomerCreditReversalResponse response =
                new CustomerCreditReversalResponse();

        response.setReversalTransactionId(reversal.getId());

        response.setReversalReferenceNumber(
                reversal.getReferenceNumber()
        );

        response.setOriginalTransactionId(
                original.getId()
        );

        response.setOriginalReferenceNumber(
                original.getReferenceNumber()
        );

        response.setCustomerId(customer.getId());
        response.setSalesInvoiceId(invoice.getId());

        response.setInvoiceNumber(
                invoice.getInvoiceNumber()
        );

        response.setReversedAmount(reversedAmount);

        response.setAvailableCreditBalance(
                availableBefore.add(reversedAmount)
        );

        response.setOutstandingBalance(
                updatedOutstanding
        );

        response.setInvoiceStatus(
                invoice.getStatus()
        );

        response.setCreatedAt(
                reversal.getCreatedAt()
        );

        return response;
    }

    // =========================
    // VALIDATE CUSTOMER ID
    // =========================

    private void validateCustomerId(Long customerId) {

        if (customerId == null || customerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer ID is required"
            );
        }
    }

    // =========================
    // VALIDATE INVOICE OWNERSHIP
    // =========================

    private void validateInvoiceOwnership(
            SalesInvoice invoice,
            Long customerId
    ) {

        if (invoice.getSales() == null
                || invoice.getSales().getCustomer() == null
                || invoice.getSales().getCustomer().getId() == null
                || !customerId.equals(
                invoice.getSales().getCustomer().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sales invoice does not belong to this customer"
            );
        }
    }

    // =========================
    // VALIDATE APPLICATION REQUEST
    // =========================

    private void validateApplicationRequest(
            CustomerCreditApplicationRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit application request is required"
            );
        }

        validateCustomerId(request.getCustomerId());

        if (request.getSalesInvoiceId() == null
                || request.getSalesInvoiceId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid sales invoice ID is required"
            );
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit amount must be greater than zero"
            );
        }

        if (amount.scale() > 2
                || amount.precision() - amount.scale() > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Credit amount supports up to 10 integer digits "
                            + "and 2 decimal places"
            );
        }

        if (request.getRemarks() != null
                && request.getRemarks().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Remarks cannot exceed 500 characters"
            );
        }
    }

    // =========================
    // AVAILABLE CREDIT BALANCE
    // =========================

    private BigDecimal calculateAvailableBalance(
            List<CustomerCreditTransaction> transactions
    ) {

        return transactions.stream()
                .map(CustomerCreditTransaction::getAmount)
                .map(this::safeAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    // =========================
    // NET APPLIED CREDIT
    // =========================

    private BigDecimal calculateNetAppliedCredit(
            List<CustomerCreditTransaction> transactions
    ) {

        BigDecimal applied = sumByType(
                transactions,
                CustomerCreditTransactionType.CREDIT_APPLIED
        );

        BigDecimal reversed = sumByType(
                transactions,
                CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
        );

        return applied.subtract(reversed).max(ZERO);
    }

    private BigDecimal getNetAppliedCredit(Long invoiceId) {

        return safeAmount(
                transactionRepository.getNetAppliedCreditForSalesInvoice(
                        invoiceId,
                        APPLIED_TYPES
                )
        ).max(ZERO);
    }

    // =========================
    // SUM BY TRANSACTION TYPE
    // =========================

    private BigDecimal sumByType(
            List<CustomerCreditTransaction> transactions,
            CustomerCreditTransactionType type
    ) {

        return transactions.stream()
                .filter(transaction ->
                        transaction.getTransactionType() == type
                )
                .map(CustomerCreditTransaction::getAmount)
                .map(this::safeAmount)
                .map(BigDecimal::abs)
                .reduce(ZERO, BigDecimal::add);
    }

    // =========================
    // OUTSTANDING CALCULATION
    // =========================

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

    // =========================
    // SALES INVOICE STATUS
    // =========================

    private void updateInvoiceStatus(
            SalesInvoice invoice,
            BigDecimal paidAmount,
            BigDecimal appliedCredit
    ) {

        BigDecimal total = safeAmount(invoice.getTotalAmount());
        BigDecimal paid = safeAmount(paidAmount);
        BigDecimal credit = safeAmount(appliedCredit);

        BigDecimal outstanding = calculateOutstanding(
                total,
                paid,
                credit
        );

        if (outstanding.compareTo(ZERO) == 0) {

            if (paid.compareTo(total) >= 0) {
                invoice.setStatus(SalesInvoiceStatus.PAID);

            } else if (credit.compareTo(total) >= 0
                    && paid.compareTo(ZERO) == 0) {
                invoice.setStatus(SalesInvoiceStatus.CREDITED);

            } else {
                invoice.setStatus(SalesInvoiceStatus.SETTLED);
            }

        } else if (paid.compareTo(ZERO) > 0
                && credit.compareTo(ZERO) > 0) {

            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_SETTLED);

        } else if (paid.compareTo(ZERO) > 0) {

            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);

        } else if (credit.compareTo(ZERO) > 0) {

            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_CREDITED);

        } else {

            invoice.setStatus(SalesInvoiceStatus.UNPAID);
        }
    }

    // =========================
    // RESPONSE MAPPING
    // =========================

    private CustomerCreditTransactionResponse convertToResponse(
            CustomerCreditTransaction transaction
    ) {

        CustomerCreditTransactionResponse response =
                new CustomerCreditTransactionResponse();

        response.setId(transaction.getId());

        response.setTransactionType(
                transaction.getTransactionType()
        );

        response.setAmount(
                transaction.getAmount()
        );

        response.setReferenceNumber(
                transaction.getReferenceNumber()
        );

        response.setRemarks(
                transaction.getRemarks()
        );

        response.setCreatedAt(
                transaction.getCreatedAt()
        );

        if (transaction.getSalesCreditNote() != null) {

            response.setSalesCreditNoteId(
                    transaction.getSalesCreditNote().getId()
            );

            response.setCreditNoteNumber(
                    transaction.getSalesCreditNote().getCreditNoteNumber()
            );
        }

        if (transaction.getSalesInvoice() != null) {

            response.setSalesInvoiceId(
                    transaction.getSalesInvoice().getId()
            );

            response.setInvoiceNumber(
                    transaction.getSalesInvoice().getInvoiceNumber()
            );
        }

        return response;
    }

    // =========================
    // COMMON
    // =========================

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : ZERO;
    }
}