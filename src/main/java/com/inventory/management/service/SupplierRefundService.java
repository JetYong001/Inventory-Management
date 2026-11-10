
package com.inventory.management.service;

import com.inventory.management.dto.request.SupplierRefundRequest;
import com.inventory.management.dto.response.SupplierRefundResponse;
import com.inventory.management.entity.Supplier;
import com.inventory.management.entity.SupplierCreditTransaction;
import com.inventory.management.entity.SupplierRefund;
import com.inventory.management.enums.SupplierCreditTransactionType;
import com.inventory.management.exception.InsufficientCreditException;
import com.inventory.management.repository.SupplierCreditTransactionRepository;
import com.inventory.management.repository.SupplierRefundRepository;
import com.inventory.management.repository.SupplierRepository;
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierRefundService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SupplierRepository supplierRepository;
    private final SupplierRefundRepository refundRepository;
    private final SupplierCreditTransactionRepository transactionRepository;

    public SupplierRefundService(
            SupplierRepository supplierRepository,
            SupplierRefundRepository refundRepository,
            SupplierCreditTransactionRepository transactionRepository
    ) {
        this.supplierRepository = supplierRepository;
        this.refundRepository = refundRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public SupplierRefundResponse createRefund(SupplierRefundRequest request) {

        validateRequest(request);

        Supplier supplier = supplierRepository
                .findByIdForUpdate(request.getSupplierId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        List<SupplierCreditTransaction> transactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplier.getId()
                );

        BigDecimal availableBalance = transactions.stream()
                .map(SupplierCreditTransaction::getAmount)
                .filter(amount -> amount != null)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal refundAmount = request.getAmount();

        if (availableBalance.compareTo(refundAmount) < 0) {
            throw new InsufficientCreditException(
                    "Insufficient supplier credit balance. Available: "
                            + availableBalance
            );
        }

        SupplierRefund refund = new SupplierRefund();

        refund.setRefundNumber(BusinessCodeGenerator.temporary());
        refund.setSupplier(supplier);
        refund.setAmount(refundAmount);
        refund.setPaymentMethod(request.getPaymentMethod());
        refund.setReferenceNumber(request.getReferenceNumber());
        refund.setRemarks(request.getRemarks());

        SupplierRefund savedRefund =
                refundRepository.saveAndFlush(refund);

        savedRefund.setRefundNumber(
                BusinessCodeGenerator.format("SRF", savedRefund.getId())
        );

        savedRefund = refundRepository.saveAndFlush(savedRefund);

        SupplierCreditTransaction transaction =
                new SupplierCreditTransaction();

        transaction.setSupplier(supplier);
        transaction.setTransactionType(
                SupplierCreditTransactionType.REFUND
        );
        transaction.setAmount(refundAmount.negate());
        transaction.setReferenceNumber(savedRefund.getRefundNumber());
        transaction.setRemarks(
                "Supplier refund - " + savedRefund.getRefundNumber()
        );

        transactionRepository.save(transaction);

        return convertToResponse(savedRefund);
    }

    public List<SupplierRefundResponse> getRefundsBySupplierId(
            Long supplierId
    ) {

        if (supplierId == null || supplierId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid supplier ID is required"
            );
        }

        if (!supplierRepository.existsById(supplierId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Supplier not found"
            );
        }

        return refundRepository
                .findBySupplierIdOrderByCreatedAtDesc(supplierId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private void validateRequest(SupplierRefundRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refund request is required"
            );
        }

        if (request.getSupplierId() == null
                || request.getSupplierId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid supplier ID is required"
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
                    "Refund amount supports up to 10 integer digits "
                            + "and 2 decimal places"
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
    }

    private SupplierRefundResponse convertToResponse(SupplierRefund refund) {

        SupplierRefundResponse response =
                new SupplierRefundResponse();

        response.setId(refund.getId());
        response.setRefundNumber(refund.getRefundNumber());
        response.setSupplierId(refund.getSupplier().getId());
        response.setSupplierName(
                refund.getSupplier().getSupplierName()
        );
        response.setAmount(refund.getAmount());
        response.setPaymentMethod(refund.getPaymentMethod());
        response.setRefundDate(refund.getRefundDate());
        response.setReferenceNumber(refund.getReferenceNumber());
        response.setRemarks(refund.getRemarks());
        response.setCreatedAt(refund.getCreatedAt());

        return response;
    }
}
