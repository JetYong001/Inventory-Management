package com.inventory.management.service;

import com.inventory.management.dto.request.SupplierRefundRequest;
import com.inventory.management.dto.response.SupplierRefundResponse;
import com.inventory.management.entity.Supplier;
import com.inventory.management.entity.SupplierCreditTransaction;
import com.inventory.management.entity.SupplierRefund;
import com.inventory.management.enums.SupplierCreditTransactionType;
import com.inventory.management.repository.SupplierCreditTransactionRepository;
import com.inventory.management.repository.SupplierRefundRepository;
import com.inventory.management.repository.SupplierRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierRefundService {

    private final SupplierRepository supplierRepository;
    private final SupplierRefundRepository refundRepository;
    private final SupplierCreditTransactionRepository transactionRepository;

    public SupplierRefundService(
            SupplierRepository supplierRepository,
            SupplierRefundRepository refundRepository,
            SupplierCreditTransactionRepository transactionRepository) {

        this.supplierRepository = supplierRepository;
        this.refundRepository = refundRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public SupplierRefundResponse createRefund(SupplierRefundRequest request) {

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }

        Supplier supplier = supplierRepository
                .findByIdForUpdate(request.getSupplierId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found"));

        List<SupplierCreditTransaction> transactions =
                transactionRepository.findBySupplierIdOrderByCreatedAtDesc(
                        supplier.getId()
                );

        BigDecimal availableBalance = transactions.stream()
                .map(SupplierCreditTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (request.getAmount().compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount exceeds available supplier credit"
            );
        }

        SupplierRefund refund = new SupplierRefund();

        refund.setRefundNumber(generateRefundNumber());
        refund.setSupplier(supplier);
        refund.setAmount(request.getAmount());
        refund.setPaymentMethod(request.getPaymentMethod());
        refund.setReferenceNumber(request.getReferenceNumber());
        refund.setRemarks(request.getRemarks());

        refund = refundRepository.saveAndFlush(refund);

        SupplierCreditTransaction transaction =
                new SupplierCreditTransaction();

        transaction.setSupplier(supplier);
        transaction.setTransactionType(
                SupplierCreditTransactionType.REFUND
        );
        transaction.setAmount(request.getAmount().negate());
        transaction.setReferenceNumber(refund.getRefundNumber());
        transaction.setRemarks(
                "Supplier refund - " + refund.getRefundNumber()
        );

        transactionRepository.save(transaction);

        return convertToResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<SupplierRefundResponse> getRefundsBySupplierId(
            Long supplierId) {

        if (!supplierRepository.existsById(supplierId)) {
            throw new EntityNotFoundException("Supplier not found");
        }

        return refundRepository
                .findBySupplierIdOrderByCreatedAtDesc(supplierId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private String generateRefundNumber() {
        return "SREF-" + UUID.randomUUID()
                .toString()
                .substring(0, 12)
                .toUpperCase();
    }

    private SupplierRefundResponse convertToResponse(
            SupplierRefund refund) {

        SupplierRefundResponse response =
                new SupplierRefundResponse();

        response.setId(refund.getId());
        response.setRefundNumber(refund.getRefundNumber());
        response.setSupplierId(refund.getSupplier().getId());
        response.setSupplierName(refund.getSupplier().getSupplierName());
        response.setAmount(refund.getAmount());
        response.setPaymentMethod(refund.getPaymentMethod());
        response.setRefundDate(refund.getRefundDate());
        response.setReferenceNumber(refund.getReferenceNumber());
        response.setRemarks(refund.getRemarks());
        response.setCreatedAt(refund.getCreatedAt());

        return response;
    }
}