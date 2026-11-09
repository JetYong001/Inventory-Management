package com.inventory.management.service;

import com.inventory.management.dto.response.OutstandingResponse;
import com.inventory.management.dto.response.OutstandingSummaryResponse;
import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.entity.PurchaseCreditNote;
import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.entity.SalesCreditNote;
import com.inventory.management.enums.CreditNoteStatus;
import com.inventory.management.enums.PurchaseInvoiceStatus;
import com.inventory.management.enums.SalesInvoiceStatus;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.PurchaseCreditNoteRepository;
import com.inventory.management.repository.PurchaseInvoiceRepository;
import com.inventory.management.repository.SalesCreditNoteRepository;
import com.inventory.management.repository.SalesInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class OutstandingService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PaymentRepository paymentRepository;
    private final SalesCreditNoteRepository salesCreditNoteRepository;
    private final PurchaseCreditNoteRepository purchaseCreditNoteRepository;

    public OutstandingService(
            SalesInvoiceRepository salesInvoiceRepository,
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            PaymentRepository paymentRepository,
            SalesCreditNoteRepository salesCreditNoteRepository,
            PurchaseCreditNoteRepository purchaseCreditNoteRepository
    ) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.paymentRepository = paymentRepository;
        this.salesCreditNoteRepository = salesCreditNoteRepository;
        this.purchaseCreditNoteRepository = purchaseCreditNoteRepository;
    }

    public List<OutstandingResponse> getCustomerOutstandings() {

        return getOutstandingSalesInvoices()
                .stream()
                .map(this::mapSalesInvoice)
                .filter(response -> response.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(
                        OutstandingResponse::getInvoiceDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public List<OutstandingResponse> getSupplierOutstandings() {

        return getOutstandingPurchaseInvoices()
                .stream()
                .map(this::mapPurchaseInvoice)
                .filter(response -> response.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(
                        OutstandingResponse::getInvoiceDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public OutstandingSummaryResponse getOutstandingSummary() {

        List<OutstandingResponse> salesOutstandings = getCustomerOutstandings();
        List<OutstandingResponse> purchaseOutstandings = getSupplierOutstandings();

        BigDecimal customerOutstanding = salesOutstandings.stream()
                .map(OutstandingResponse::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal supplierOutstanding = purchaseOutstandings.stream()
                .map(OutstandingResponse::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        OutstandingSummaryResponse response = new OutstandingSummaryResponse();

        response.setTotalCustomerOutstanding(customerOutstanding);
        response.setTotalSupplierOutstanding(supplierOutstanding);
        response.setUnpaidSalesInvoiceCount(salesOutstandings.size());
        response.setUnpaidPurchaseInvoiceCount(purchaseOutstandings.size());
        response.setTotalOutstandingInvoices(
                salesOutstandings.size() + purchaseOutstandings.size()
        );

        return response;
    }

    private List<SalesInvoice> getOutstandingSalesInvoices() {

        return Stream.of(
                        SalesInvoiceStatus.UNPAID,
                        SalesInvoiceStatus.PARTIALLY_PAID,
                        SalesInvoiceStatus.PARTIALLY_CREDITED,
                        SalesInvoiceStatus.PARTIALLY_SETTLED
                )
                .flatMap(status ->
                        salesInvoiceRepository
                                .findAllByStatusOrderByInvoiceDateDesc(status)
                                .stream()
                )
                .toList();
    }

    private List<PurchaseInvoice> getOutstandingPurchaseInvoices() {

        return Stream.of(
                        PurchaseInvoiceStatus.UNPAID,
                        PurchaseInvoiceStatus.PARTIALLY_PAID,
                        PurchaseInvoiceStatus.PARTIALLY_CREDITED,
                        PurchaseInvoiceStatus.PARTIALLY_SETTLED
                )
                .flatMap(status ->
                        purchaseInvoiceRepository
                                .findAllByStatusOrderByInvoiceDateDesc(status)
                                .stream()
                )
                .toList();
    }

    private OutstandingResponse mapSalesInvoice(SalesInvoice invoice) {

        BigDecimal totalAmount = safeAmount(invoice.getTotalAmount());

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalSalesInvoicePayments(invoice.getId())
        );

        BigDecimal creditAmount = salesCreditNoteRepository
                .findBySalesInvoiceId(invoice.getId())
                .stream()
                .filter(creditNote -> creditNote.getStatus() == CreditNoteStatus.ISSUED)
                .map(SalesCreditNote::getTotalAmount)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingAmount = calculateOutstanding(
                totalAmount,
                paidAmount,
                creditAmount
        );

        OutstandingResponse response = new OutstandingResponse();

        response.setInvoiceId(invoice.getId());
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

    private OutstandingResponse mapPurchaseInvoice(PurchaseInvoice invoice) {

        BigDecimal totalAmount = safeAmount(invoice.getTotalAmount());

        BigDecimal paidAmount = safeAmount(
                paymentRepository.getTotalPurchaseInvoicePayments(invoice.getId())
        );

        BigDecimal creditAmount = purchaseCreditNoteRepository
                .findByPurchaseInvoiceId(invoice.getId())
                .stream()
                .filter(creditNote -> creditNote.getStatus() == CreditNoteStatus.ISSUED)
                .map(PurchaseCreditNote::getTotalAmount)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingAmount = calculateOutstanding(
                totalAmount,
                paidAmount,
                creditAmount
        );

        OutstandingResponse response = new OutstandingResponse();

        response.setInvoiceId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());
        response.setInvoiceDate(invoice.getInvoiceDate());

        response.setPartyCode(invoice.getSupplierCode());
        response.setPartyName(invoice.getSupplierName());
        response.setTotalAmount(totalAmount);
        response.setOutstandingAmount(outstandingAmount);

        return response;
    }

    private BigDecimal calculateOutstanding(
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal creditAmount
    ) {
        BigDecimal outstanding = totalAmount
                .subtract(paidAmount)
                .subtract(creditAmount);

        if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
            outstanding = BigDecimal.ZERO;
        }

        return outstanding.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}