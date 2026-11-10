
package com.inventory.management.service;

import com.inventory.management.dto.response.OutstandingResponse;
import com.inventory.management.dto.response.OutstandingSummaryResponse;
import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.entity.SupplierCreditTransaction;
import com.inventory.management.enums.CustomerCreditTransactionType;
import com.inventory.management.enums.SupplierCreditTransactionType;
import com.inventory.management.repository.CustomerCreditTransactionRepository;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.PurchaseInvoiceRepository;
import com.inventory.management.repository.SalesInvoiceRepository;
import com.inventory.management.repository.SupplierCreditTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OutstandingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final List<CustomerCreditTransactionType> CUSTOMER_APPLIED_TYPES =
            List.of(
                    CustomerCreditTransactionType.CREDIT_APPLIED,
                    CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
            );

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerCreditTransactionRepository customerCreditTransactionRepository;
    private final SupplierCreditTransactionRepository supplierCreditTransactionRepository;

    public OutstandingService(
            SalesInvoiceRepository salesInvoiceRepository,
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            PaymentRepository paymentRepository,
            CustomerCreditTransactionRepository customerCreditTransactionRepository,
            SupplierCreditTransactionRepository supplierCreditTransactionRepository
    ) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.paymentRepository = paymentRepository;
        this.customerCreditTransactionRepository = customerCreditTransactionRepository;
        this.supplierCreditTransactionRepository = supplierCreditTransactionRepository;
    }

    public List<OutstandingResponse> getCustomerOutstandings() {

        return salesInvoiceRepository.findAll()
                .stream()
                .map(this::mapSalesInvoice)
                .filter(response ->
                        response.getOutstandingAmount().compareTo(ZERO) > 0
                )
                .sorted(Comparator.comparing(
                        OutstandingResponse::getInvoiceDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public List<OutstandingResponse> getSupplierOutstandings() {

        return purchaseInvoiceRepository.findAll()
                .stream()
                .map(this::mapPurchaseInvoice)
                .filter(response ->
                        response.getOutstandingAmount().compareTo(ZERO) > 0
                )
                .sorted(Comparator.comparing(
                        OutstandingResponse::getInvoiceDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public OutstandingSummaryResponse getOutstandingSummary() {

        List<OutstandingResponse> customerOutstandings =
                getCustomerOutstandings();

        List<OutstandingResponse> supplierOutstandings =
                getSupplierOutstandings();

        BigDecimal customerOutstanding = customerOutstandings.stream()
                .map(OutstandingResponse::getOutstandingAmount)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal supplierOutstanding = supplierOutstandings.stream()
                .map(OutstandingResponse::getOutstandingAmount)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        OutstandingSummaryResponse response =
                new OutstandingSummaryResponse();

        response.setTotalCustomerOutstanding(customerOutstanding);
        response.setTotalSupplierOutstanding(supplierOutstanding);

        response.setUnpaidSalesInvoiceCount(
                customerOutstandings.size()
        );

        response.setUnpaidPurchaseInvoiceCount(
                supplierOutstandings.size()
        );

        response.setTotalOutstandingInvoices(
                customerOutstandings.size()
                        + supplierOutstandings.size()
        );

        return response;
    }

    private OutstandingResponse mapSalesInvoice(SalesInvoice invoice) {

        Long invoiceId = invoice.getId();

        BigDecimal totalAmount = safeAmount(
                invoice.getTotalAmount()
        );

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(invoiceId)
        );

        BigDecimal appliedCredit = safeAmount(
                customerCreditTransactionRepository
                        .getNetAppliedCreditForSalesInvoice(
                                invoiceId,
                                CUSTOMER_APPLIED_TYPES
                        )
        ).max(ZERO);

        BigDecimal outstandingAmount = calculateOutstanding(
                totalAmount,
                paidAmount,
                appliedCredit
        );

        OutstandingResponse response = new OutstandingResponse();

        response.setInvoiceId(invoiceId);
        response.setInvoiceNumber(invoice.getInvoiceNumber());

        response.setInvoiceDate(
                invoice.getInvoiceDate() == null
                        ? null
                        : invoice.getInvoiceDate().toLocalDate()
        );

        response.setPartyCode(invoice.getCustomerCode());
        response.setPartyName(invoice.getCustomerName());

        response.setTotalAmount(totalAmount);
        response.setOutstandingAmount(outstandingAmount);

        return response;
    }

    private OutstandingResponse mapPurchaseInvoice(
            PurchaseInvoice invoice
    ) {

        Long invoiceId = invoice.getId();

        BigDecimal totalAmount = safeAmount(
                invoice.getTotalAmount()
        );

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalPurchaseInvoicePayments(invoiceId)
        );

        BigDecimal appliedCredit = calculateNetSupplierCredit(invoiceId);

        BigDecimal outstandingAmount = calculateOutstanding(
                totalAmount,
                paidAmount,
                appliedCredit
        );

        OutstandingResponse response = new OutstandingResponse();

        response.setInvoiceId(invoiceId);
        response.setInvoiceNumber(invoice.getInvoiceNumber());
        response.setInvoiceDate(invoice.getInvoiceDate());

        response.setPartyCode(invoice.getSupplierCode());
        response.setPartyName(invoice.getSupplierName());

        response.setTotalAmount(totalAmount);
        response.setOutstandingAmount(outstandingAmount);

        return response;
    }

    private BigDecimal calculateNetSupplierCredit(Long invoiceId) {

        List<SupplierCreditTransaction> transactions =
                supplierCreditTransactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(
                                invoiceId
                        );

        BigDecimal applied = transactions.stream()
                .filter(transaction ->
                        transaction.getTransactionType()
                                == SupplierCreditTransactionType.CREDIT_APPLIED
                )
                .map(SupplierCreditTransaction::getAmount)
                .map(this::safeAmount)
                .map(BigDecimal::abs)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal reversed = transactions.stream()
                .filter(transaction ->
                        transaction.getTransactionType()
                                == SupplierCreditTransactionType.CREDIT_APPLICATION_REVERSED
                )
                .map(SupplierCreditTransaction::getAmount)
                .map(this::safeAmount)
                .map(BigDecimal::abs)
                .reduce(ZERO, BigDecimal::add);

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
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }
}
