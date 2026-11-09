
package com.inventory.management.service;

import com.inventory.management.dto.request.CustomerRefundRequest;
import com.inventory.management.dto.response.CustomerRefundResponse;
import com.inventory.management.entity.Customer;
import com.inventory.management.entity.CustomerCreditTransaction;
import com.inventory.management.entity.CustomerRefund;
import com.inventory.management.enums.CustomerCreditTransactionType;
import com.inventory.management.exception.InsufficientCreditException;
import com.inventory.management.repository.CustomerCreditTransactionRepository;
import com.inventory.management.repository.CustomerRefundRepository;
import com.inventory.management.repository.CustomerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerRefundService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CustomerRepository customerRepository;
    private final CustomerCreditTransactionRepository creditTransactionRepository;
    private final CustomerRefundRepository refundRepository;

    public CustomerRefundService(
            CustomerRepository customerRepository,
            CustomerCreditTransactionRepository creditTransactionRepository,
            CustomerRefundRepository refundRepository
    ) {
        this.customerRepository = customerRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional
    public CustomerRefundResponse createRefund(CustomerRefundRequest request) {

        validateRequest(request);

        Customer customer = customerRepository.findByIdForUpdate(
                request.getCustomerId()
        ).orElseThrow(() ->
                new RuntimeException("Customer not found")
        );

        List<CustomerCreditTransaction> transactions =
                creditTransactionRepository
                        .findByCustomerIdOrderByCreatedAtDesc(customer.getId());

        BigDecimal availableBalance = transactions.stream()
                .map(CustomerCreditTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal refundAmount = request.getAmount();

        if (availableBalance.compareTo(refundAmount) < 0) {
            throw new InsufficientCreditException(
                    "Insufficient customer credit balance"
            );
        }

        String refundNumber = generateRefundNumber();

        CustomerRefund refund = new CustomerRefund();
        refund.setCustomer(customer);
        refund.setRefundNumber(refundNumber);
        refund.setAmount(refundAmount);
        refund.setPaymentMethod(request.getPaymentMethod());
        refund.setRefundDate(
                request.getRefundDate() != null
                        ? request.getRefundDate()
                        : LocalDate.now()
        );
        refund.setReferenceNumber(request.getReferenceNumber());
        refund.setRemarks(request.getRemarks());

        CustomerRefund savedRefund = refundRepository.save(refund);

        CustomerCreditTransaction ledgerTransaction =
                new CustomerCreditTransaction();

        ledgerTransaction.setCustomer(customer);
        ledgerTransaction.setTransactionType(
                CustomerCreditTransactionType.REFUND
        );
        ledgerTransaction.setAmount(refundAmount.negate());
        ledgerTransaction.setReferenceNumber(refundNumber);
        ledgerTransaction.setRemarks(
                "Customer refund via " + request.getPaymentMethod()
        );

        CustomerCreditTransaction savedLedger =
                creditTransactionRepository.save(ledgerTransaction);

        return convertToResponse(savedRefund, savedLedger.getId());
    }

    public List<CustomerRefundResponse> getCustomerRefunds(Long customerId) {

        if (customerId == null || customerId <= 0) {
            throw new RuntimeException("Invalid customer ID");
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new RuntimeException("Customer not found")
                );

        return refundRepository
                .findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(refund -> {
                    CustomerCreditTransaction ledgerTransaction =
                            creditTransactionRepository
                                    .findByReferenceNumberAndTransactionType(
                                            refund.getRefundNumber(),
                                            CustomerCreditTransactionType.REFUND
                                    );

                    Long ledgerTransactionId = ledgerTransaction != null
                            ? ledgerTransaction.getId()
                            : null;

                    return convertToResponse(
                            refund,
                            ledgerTransactionId
                    );
                })
                .toList();
    }

    private void validateRequest(CustomerRefundRequest request) {

        if (request == null) {
            throw new RuntimeException("Refund request cannot be null");
        }

        if (request.getCustomerId() == null
                || request.getCustomerId() <= 0) {
            throw new RuntimeException("Invalid customer ID");
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new RuntimeException(
                    "Refund amount must be greater than zero"
            );
        }

        if (amount.scale() > 2) {
            throw new RuntimeException(
                    "Refund amount cannot have more than 2 decimal places"
            );
        }

        if (amount.precision() - amount.scale() > 10) {
            throw new RuntimeException(
                    "Refund amount exceeds the maximum allowed value"
            );
        }

        if (request.getPaymentMethod() == null) {
            throw new RuntimeException("Payment method is required");
        }

        if (request.getReferenceNumber() != null
                && request.getReferenceNumber().length() > 100) {
            throw new RuntimeException(
                    "Reference number cannot exceed 100 characters"
            );
        }

        if (request.getRemarks() != null
                && request.getRemarks().length() > 500) {
            throw new RuntimeException(
                    "Remarks cannot exceed 500 characters"
            );
        }

        if (request.getRefundDate() != null
                && request.getRefundDate().isAfter(LocalDate.now())) {
            throw new RuntimeException(
                    "Refund date cannot be in the future"
            );
        }
    }

    private String generateRefundNumber() {

        return "CRF-"
                + Year.now().getValue()
                + "-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private CustomerRefundResponse convertToResponse(
            CustomerRefund refund,
            Long ledgerTransactionId
    ) {

        CustomerRefundResponse response =
                new CustomerRefundResponse();

        response.setId(refund.getId());
        response.setCustomerId(refund.getCustomer().getId());
        response.setCustomerName(
                refund.getCustomer().getCustomerName()
        );
        response.setRefundNumber(refund.getRefundNumber());
        response.setAmount(refund.getAmount());
        response.setPaymentMethod(refund.getPaymentMethod());
        response.setRefundDate(refund.getRefundDate());
        response.setReferenceNumber(refund.getReferenceNumber());
        response.setRemarks(refund.getRemarks());
        response.setLedgerTransactionId(ledgerTransactionId);
        response.setCreatedAt(refund.getCreatedAt());

        return response;
    }
}
