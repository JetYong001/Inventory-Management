package com.inventory.management.service;

import com.inventory.management.dto.request.PaymentRequest;
import com.inventory.management.dto.response.PaymentResponse;
import com.inventory.management.entity.Payment;
import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.entity.SupplierCreditTransaction;
import com.inventory.management.enums.PaymentType;
import com.inventory.management.enums.PurchaseInvoiceStatus;
import com.inventory.management.enums.SalesInvoiceStatus;
import com.inventory.management.enums.CustomerCreditTransactionType;
import com.inventory.management.enums.SupplierCreditTransactionType;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.PurchaseInvoiceRepository;
import com.inventory.management.repository.SalesInvoiceRepository;
import com.inventory.management.repository.CustomerCreditTransactionRepository;
import com.inventory.management.repository.SupplierCreditTransactionRepository;
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class PaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final List<CustomerCreditTransactionType> SALES_CREDIT_TYPES =
            List.of(
                    CustomerCreditTransactionType.CREDIT_APPLIED,
                    CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
            );

    private final PaymentRepository paymentRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final CustomerCreditTransactionRepository customerCreditTransactionRepository;
    private final SupplierCreditTransactionRepository supplierCreditTransactionRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            CustomerCreditTransactionRepository customerCreditTransactionRepository,
            SupplierCreditTransactionRepository supplierCreditTransactionRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.customerCreditTransactionRepository = customerCreditTransactionRepository;
        this.supplierCreditTransactionRepository = supplierCreditTransactionRepository;
    }

    public PaymentResponse recordCustomerPayment(
            Long invoiceId,
            PaymentRequest request
    ) {
        validateInvoiceId(invoiceId);
        validateRequest(request);

        SalesInvoice invoice = salesInvoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales invoice not found with ID: " + invoiceId
                ));

        BigDecimal totalAmount = safeAmount(invoice.getTotalAmount());

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(invoiceId)
        );

        BigDecimal appliedCredit = getAppliedSalesCreditAmount(invoiceId);

        BigDecimal remainingAmount = calculateRemainingAmount(
                totalAmount,
                paidAmount,
                appliedCredit
        );

        validatePaymentAmount(request.getAmount(), remainingAmount);

        Payment payment = createPayment(
                request,
                PaymentType.CUSTOMER_RECEIPT
        );

        payment.setSalesInvoice(invoice);

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        savedPayment.setPaymentNumber(
                BusinessCodeGenerator.format("PAY", savedPayment.getId())
        );

        savedPayment = paymentRepository.save(savedPayment);

        updateSalesInvoiceStatus(
                invoice,
                paidAmount.add(request.getAmount()),
                appliedCredit
        );

        return convertToResponse(savedPayment);
    }

    public PaymentResponse recordSupplierPayment(
            Long invoiceId,
            PaymentRequest request
    ) {
        validateInvoiceId(invoiceId);
        validateRequest(request);

        PurchaseInvoice invoice = purchaseInvoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase invoice not found with ID: " + invoiceId
                ));

        BigDecimal totalAmount = safeAmount(invoice.getTotalAmount());

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalPurchaseInvoicePayments(invoiceId)
        );

        BigDecimal appliedCredit = getAppliedSupplierCreditAmount(invoiceId);

        BigDecimal remainingAmount = calculateRemainingAmount(
                totalAmount,
                paidAmount,
                appliedCredit
        );

        validatePaymentAmount(request.getAmount(), remainingAmount);

        Payment payment = createPayment(
                request,
                PaymentType.SUPPLIER_PAYMENT
        );

        payment.setPurchaseInvoice(invoice);

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        savedPayment.setPaymentNumber(
                BusinessCodeGenerator.format("PAY", savedPayment.getId())
        );

        savedPayment = paymentRepository.save(savedPayment);

        updatePurchaseInvoiceStatus(
                invoice,
                paidAmount.add(request.getAmount()),
                appliedCredit
        );

        return convertToResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByPaymentDateDesc()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found with ID: " + paymentId
                ));

        return convertToResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentBySalesInvoice(Long invoiceId) {
        validateInvoiceId(invoiceId);

        if (!salesInvoiceRepository.existsById(invoiceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sales invoice not found with ID: " + invoiceId
            );
        }

        return paymentRepository
                .findAllBySalesInvoice_IdOrderByPaymentDateDesc(invoiceId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentByPurchaseInvoice(Long invoiceId) {
        validateInvoiceId(invoiceId);

        if (!purchaseInvoiceRepository.existsById(invoiceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Purchase invoice not found with ID: " + invoiceId
            );
        }

        return paymentRepository
                .findAllByPurchaseInvoice_IdOrderByPaymentDateDesc(invoiceId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private Payment createPayment(
            PaymentRequest request,
            PaymentType paymentType
    ) {
        Payment payment = new Payment();

        payment.setPaymentNumber(BusinessCodeGenerator.temporary());
        payment.setPaymentType(paymentType);
        payment.setPaymentMethod(request.getPaymentMethod());

        payment.setPaymentDate(
                request.getPaymentDate() != null
                        ? request.getPaymentDate()
                        : LocalDate.now()
        );

        payment.setAmount(request.getAmount());
        payment.setReferenceNumber(request.getReferenceNumber());
        payment.setRemarks(request.getRemarks());

        return payment;
    }

    private void validateInvoiceId(Long invoiceId) {
        if (invoiceId == null || invoiceId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice ID must be greater than zero"
            );
        }
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment request is required"
            );
        }

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must be greater than zero"
            );
        }

        if (amount.scale() > 2
                || amount.precision() - amount.scale() > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount supports up to 10 integer digits and 2 decimal places"
            );
        }

        if (request.getPaymentMethod() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment method is required"
            );
        }

        if (request.getPaymentDate() != null
                && request.getPaymentDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment date cannot be in the future"
            );
        }
    }

    private void validatePaymentAmount(
            BigDecimal paymentAmount,
            BigDecimal remainingAmount
    ) {
        if (remainingAmount.compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This invoice has no outstanding balance"
            );
        }

        if (paymentAmount.compareTo(remainingAmount) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount exceeds remaining balance. Remaining amount: "
                            + remainingAmount
            );
        }
    }

    private BigDecimal calculateRemainingAmount(
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal creditedAmount
    ) {
        return safeAmount(totalAmount)
                .subtract(safeAmount(paidAmount))
                .subtract(safeAmount(creditedAmount))
                .max(ZERO);
    }

    private BigDecimal getAppliedSalesCreditAmount(Long invoiceId) {
        BigDecimal amount =
                customerCreditTransactionRepository.getNetAppliedCreditForSalesInvoice(
                        invoiceId,
                        SALES_CREDIT_TYPES
                );

        return safeAmount(amount).max(ZERO);
    }

    private BigDecimal getAppliedSupplierCreditAmount(Long invoiceId) {
        List<SupplierCreditTransaction> transactions =
                supplierCreditTransactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(invoiceId);

        BigDecimal applied = transactions.stream()
                .filter(t -> t.getTransactionType()
                        == SupplierCreditTransactionType.CREDIT_APPLIED)
                .map(t -> safeAmount(t.getAmount()).abs())
                .reduce(ZERO, BigDecimal::add);

        BigDecimal reversed = transactions.stream()
                .filter(t -> t.getTransactionType()
                        == SupplierCreditTransactionType.CREDIT_APPLICATION_REVERSED)
                .map(t -> safeAmount(t.getAmount()).abs())
                .reduce(ZERO, BigDecimal::add);

        return applied.subtract(reversed).max(ZERO);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : ZERO;
    }

    private void updateSalesInvoiceStatus(
            SalesInvoice invoice,
            BigDecimal paidAmount,
            BigDecimal creditedAmount
    ) {
        BigDecimal total = safeAmount(invoice.getTotalAmount());

        BigDecimal remaining = calculateRemainingAmount(
                total,
                paidAmount,
                creditedAmount
        );

        if (remaining.compareTo(ZERO) == 0) {
            if (paidAmount.compareTo(total) >= 0) {
                invoice.setStatus(SalesInvoiceStatus.PAID);
            } else if (creditedAmount.compareTo(total) >= 0) {
                invoice.setStatus(SalesInvoiceStatus.CREDITED);
            } else {
                invoice.setStatus(SalesInvoiceStatus.SETTLED);
            }
        } else if (paidAmount.compareTo(ZERO) > 0
                && creditedAmount.compareTo(ZERO) > 0) {
            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_SETTLED);
        } else if (paidAmount.compareTo(ZERO) > 0) {
            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
        } else if (creditedAmount.compareTo(ZERO) > 0) {
            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_CREDITED);
        } else {
            invoice.setStatus(SalesInvoiceStatus.UNPAID);
        }
    }

    private void updatePurchaseInvoiceStatus(
            PurchaseInvoice invoice,
            BigDecimal paidAmount,
            BigDecimal creditedAmount
    ) {
        BigDecimal total = safeAmount(invoice.getTotalAmount());

        BigDecimal remaining = calculateRemainingAmount(
                total,
                paidAmount,
                creditedAmount
        );

        if (remaining.compareTo(ZERO) == 0) {
            if (paidAmount.compareTo(total) >= 0) {
                invoice.setStatus(PurchaseInvoiceStatus.PAID);
            } else if (creditedAmount.compareTo(total) >= 0) {
                invoice.setStatus(PurchaseInvoiceStatus.CREDITED);
            } else {
                invoice.setStatus(PurchaseInvoiceStatus.SETTLED);
            }
        } else if (paidAmount.compareTo(ZERO) > 0
                && creditedAmount.compareTo(ZERO) > 0) {
            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_SETTLED);
        } else if (paidAmount.compareTo(ZERO) > 0) {
            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_PAID);
        } else if (creditedAmount.compareTo(ZERO) > 0) {
            invoice.setStatus(PurchaseInvoiceStatus.PARTIALLY_CREDITED);
        } else {
            invoice.setStatus(PurchaseInvoiceStatus.UNPAID);
        }
    }

    private PaymentResponse convertToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();

        response.setId(payment.getId());
        response.setPaymentNumber(payment.getPaymentNumber());
        response.setPaymentType(payment.getPaymentType());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setPaymentDate(payment.getPaymentDate());
        response.setAmount(payment.getAmount());
        response.setReferenceNumber(payment.getReferenceNumber());
        response.setRemarks(payment.getRemarks());
        response.setCreatedAt(payment.getCreatedAt());

        if (payment.getSalesInvoice() != null) {
            response.setSalesInvoiceId(payment.getSalesInvoice().getId());
            response.setSalesInvoiceNumber(
                    payment.getSalesInvoice().getInvoiceNumber()
            );
        }

        if (payment.getPurchaseInvoice() != null) {
            response.setPurchaseInvoiceId(payment.getPurchaseInvoice().getId());
            response.setPurchaseInvoiceNumber(
                    payment.getPurchaseInvoice().getInvoiceNumber()
            );
        }

        return response;
    }
}