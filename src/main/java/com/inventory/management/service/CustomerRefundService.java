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
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

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

    // ========================================
    // CREATE CUSTOMER REFUND
    // ========================================

    @Transactional
    public CustomerRefundResponse createRefund(
            CustomerRefundRequest request,
            String rawIdempotencyKey
    ) {
        validateRequest(request);

        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String requestHash = createRequestHash(request);

        Optional<CustomerRefundResponse> existingRefund =
                findExistingRefund(idempotencyKey, requestHash);

        if (existingRefund.isPresent()) {
            return existingRefund.get();
        }

        Customer customer = customerRepository.findByIdForUpdate(
                request.getCustomerId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Customer not found"
        ));

        // Check again after locking the customer.
        existingRefund = findExistingRefund(idempotencyKey, requestHash);

        if (existingRefund.isPresent()) {
            return existingRefund.get();
        }

        List<CustomerCreditTransaction> transactions =
                creditTransactionRepository
                        .findByCustomerIdOrderByCreatedAtDesc(customer.getId());

        BigDecimal availableBalance = transactions.stream()
                .map(CustomerCreditTransaction::getAmount)
                .filter(amount -> amount != null)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal refundAmount = request.getAmount();

        if (availableBalance.compareTo(refundAmount) < 0) {
            throw new InsufficientCreditException(
                    "Insufficient customer credit balance. Available: "
                            + availableBalance
            );
        }

        CustomerRefund refund = new CustomerRefund();

        refund.setRefundNumber(BusinessCodeGenerator.temporary());
        refund.setCustomer(customer);
        refund.setAmount(refundAmount);
        refund.setPaymentMethod(request.getPaymentMethod());
        refund.setRefundDate(
                request.getRefundDate() != null
                        ? request.getRefundDate()
                        : LocalDate.now()
        );
        refund.setReferenceNumber(normalizeOptional(request.getReferenceNumber()));
        refund.setRemarks(normalizeOptional(request.getRemarks()));
        refund.setIdempotencyKey(idempotencyKey);
        refund.setRequestHash(requestHash);

        CustomerRefund savedRefund = saveRefund(refund);

        CustomerCreditTransaction ledgerTransaction =
                new CustomerCreditTransaction();

        ledgerTransaction.setCustomer(customer);
        ledgerTransaction.setTransactionType(
                CustomerCreditTransactionType.REFUND
        );
        ledgerTransaction.setAmount(refundAmount.negate());
        ledgerTransaction.setReferenceNumber(
                savedRefund.getRefundNumber()
        );
        ledgerTransaction.setRemarks(
                "Customer refund via " + request.getPaymentMethod()
        );

        CustomerCreditTransaction savedLedger =
                creditTransactionRepository.save(ledgerTransaction);

        return convertToResponse(
                savedRefund,
                savedLedger.getId()
        );
    }

    // ========================================
    // GET CUSTOMER REFUNDS
    // ========================================

    public List<CustomerRefundResponse> getCustomerRefunds(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer ID is required"
            );
        }

        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        return refundRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(refund -> {
                    CustomerCreditTransaction ledgerTransaction =
                            creditTransactionRepository
                                    .findByReferenceNumberAndTransactionType(
                                            refund.getRefundNumber(),
                                            CustomerCreditTransactionType.REFUND
                                    );

                    Long ledgerTransactionId =
                            ledgerTransaction != null
                                    ? ledgerTransaction.getId()
                                    : null;

                    return convertToResponse(
                            refund,
                            ledgerTransactionId
                    );
                })
                .toList();
    }

    // ========================================
    // IDEMPOTENCY
    // ========================================

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

    private String createRequestHash(CustomerRefundRequest request) {
        String refundDate = request.getRefundDate() == null
                ? ""
                : request.getRefundDate().toString();

        String canonicalRequest = String.join("|",
                request.getCustomerId().toString(),
                request.getAmount().stripTrailingZeros().toPlainString(),
                request.getPaymentMethod().name(),
                refundDate,
                normalizeOptional(request.getReferenceNumber()),
                normalizeOptional(request.getRemarks())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }

    private Optional<CustomerRefundResponse> findExistingRefund(
            String idempotencyKey,
            String requestHash
    ) {
        return refundRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingRefund -> {
                    if (!requestHash.equals(existingRefund.getRequestHash())) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "This Idempotency-Key has already been used with a different refund request"
                        );
                    }

                    CustomerCreditTransaction ledgerTransaction =
                            creditTransactionRepository
                                    .findByReferenceNumberAndTransactionType(
                                            existingRefund.getRefundNumber(),
                                            CustomerCreditTransactionType.REFUND
                                    );

                    Long ledgerTransactionId =
                            ledgerTransaction != null
                                    ? ledgerTransaction.getId()
                                    : null;

                    return convertToResponse(
                            existingRefund,
                            ledgerTransactionId
                    );
                });
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private CustomerRefund saveRefund(CustomerRefund refund) {
        try {
            CustomerRefund savedRefund =
                    refundRepository.saveAndFlush(refund);

            savedRefund.setRefundNumber(
                    BusinessCodeGenerator.format("CRF", savedRefund.getId())
            );

            return refundRepository.saveAndFlush(savedRefund);

        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refund could not be saved because the request key or refund number conflicts with an existing record",
                    exception
            );
        }
    }

    // ========================================
    // VALIDATION
    // ========================================

    private void validateRequest(CustomerRefundRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refund request is required"
            );
        }

        if (request.getCustomerId() == null
                || request.getCustomerId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid customer ID is required"
            );
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refund amount must be greater than zero"
            );
        }

        if (amount.scale() > 2
                || amount.precision() - amount.scale() > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refund amount supports up to 10 integer digits and 2 decimal places"
            );
        }

        if (request.getPaymentMethod() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment method is required"
            );
        }

        if (request.getReferenceNumber() != null
                && request.getReferenceNumber().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reference number cannot exceed 100 characters"
            );
        }

        if (request.getRemarks() != null
                && request.getRemarks().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Remarks cannot exceed 500 characters"
            );
        }

        if (request.getRefundDate() != null
                && request.getRefundDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refund date cannot be in the future"
            );
        }
    }

    // ========================================
    // RESPONSE
    // ========================================

    private CustomerRefundResponse convertToResponse(
            CustomerRefund refund,
            Long ledgerTransactionId
    ) {
        CustomerRefundResponse response = new CustomerRefundResponse();

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