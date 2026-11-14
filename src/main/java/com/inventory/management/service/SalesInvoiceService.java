package com.inventory.management.service;

import com.inventory.management.dto.response.SalesInvoiceItemResponse;
import com.inventory.management.dto.response.SalesInvoiceResponse;
import com.inventory.management.entity.Customer;
import com.inventory.management.entity.Sales;
import com.inventory.management.entity.SalesItem;
import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.entity.SalesInvoiceItem;
import com.inventory.management.enums.CustomerCreditTransactionType;
import com.inventory.management.enums.SalesInvoiceStatus;
import com.inventory.management.exception.InvalidSalesOperationException;
import com.inventory.management.exception.SalesInvoiceNotFoundException;
import com.inventory.management.repository.CustomerCreditTransactionRepository;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.SalesInvoiceRepository;
import com.inventory.management.repository.SalesRepository;
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SalesInvoiceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final List<CustomerCreditTransactionType> SALES_CREDIT_TYPES =
            List.of(
                    CustomerCreditTransactionType.CREDIT_APPLIED,
                    CustomerCreditTransactionType.CREDIT_APPLICATION_REVERSED
            );

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesRepository salesRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerCreditTransactionRepository customerCreditTransactionRepository;

    public SalesInvoiceService(
            SalesInvoiceRepository salesInvoiceRepository,
            SalesRepository salesRepository,
            PaymentRepository paymentRepository,
            CustomerCreditTransactionRepository customerCreditTransactionRepository
    ) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.salesRepository = salesRepository;
        this.paymentRepository = paymentRepository;
        this.customerCreditTransactionRepository = customerCreditTransactionRepository;
    }

    // ========================================
    // CREATE INVOICE FROM SALES
    // ========================================

    @Transactional
    public SalesInvoiceResponse createInvoiceFromSales(Long salesId) {

        Sales sales = salesRepository.findByIdForUpdate(salesId)
                .orElseThrow(() ->
                        new InvalidSalesOperationException(
                                "Sales not found: " + salesId
                        ));

        if (!"COMPLETED".equals(sales.getStatus().name())) {
            throw new InvalidSalesOperationException(
                    "Only COMPLETED sales can generate an invoice"
            );
        }

        if (salesInvoiceRepository.existsBySales_Id(salesId)) {
            throw new InvalidSalesOperationException(
                    "An invoice already exists for this sales"
            );
        }

        Customer customer = sales.getCustomer();

        SalesInvoice invoice = new SalesInvoice();

        invoice.setInvoiceNumber(BusinessCodeGenerator.temporary());
        invoice.setSales(sales);
        invoice.setTotalAmount(sales.getTotalAmount());
        invoice.setStatus(SalesInvoiceStatus.UNPAID);

        invoice.setCustomerCode(customer.getCustomerCode());
        invoice.setCustomerName(customer.getCustomerName());
        invoice.setCustomerPhone(customer.getPhone());
        invoice.setCustomerEmail(customer.getEmail());
        invoice.setCustomerAddress(customer.getAddress());

        List<SalesInvoiceItem> invoiceItems = new ArrayList<>();

        for (SalesItem salesItem : sales.getItems()) {

            SalesInvoiceItem invoiceItem = new SalesInvoiceItem();

            String productCode = salesItem.getProductCodeSnapshot();
            String productName = salesItem.getProductNameSnapshot();

            if (productCode == null || productCode.isBlank()
                    || productName == null || productName.isBlank()) {
                productCode = salesItem.getProduct().getProductCode();
                productName = salesItem.getProduct().getProductName();
            }

            invoiceItem.setProductCode(productCode);
            invoiceItem.setProductName(productName);
            invoiceItem.setQuantity(salesItem.getQuantity());
            invoiceItem.setUnitPrice(salesItem.getUnitPrice());
            invoiceItem.setSubtotal(salesItem.getSubtotal());
            invoiceItem.setInvoice(invoice);

            invoiceItems.add(invoiceItem);
        }

        invoice.setItems(invoiceItems);

        SalesInvoice savedInvoice =
                salesInvoiceRepository.saveAndFlush(invoice);

        savedInvoice.setInvoiceNumber(
                BusinessCodeGenerator.format("SINV", savedInvoice.getId())
        );

        savedInvoice = salesInvoiceRepository.save(savedInvoice);

        return convertToResponse(savedInvoice);
    }

    // ========================================
    // GET ALL INVOICES
    // ========================================

    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getAllInvoices() {

        return salesInvoiceRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // ========================================
    // GET INVOICE BY ID
    // ========================================

    @Transactional(readOnly = true)
    public SalesInvoiceResponse getInvoiceById(Long invoiceId) {

        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() ->
                        new SalesInvoiceNotFoundException(
                                "Sales invoice not found: " + invoiceId
                        ));

        return convertToResponse(invoice);
    }

    // ========================================
    // GET INVOICE BY SALES ID
    // ========================================

    @Transactional(readOnly = true)
    public SalesInvoiceResponse getInvoiceBySalesId(Long salesId) {

        SalesInvoice invoice = salesInvoiceRepository.findBySales_Id(salesId)
                .orElseThrow(() ->
                        new SalesInvoiceNotFoundException(
                                "Invoice not found for sales ID: " + salesId
                        ));

        return convertToResponse(invoice);
    }

    // ========================================
    // PAYMENT CALCULATION
    // ========================================

    private BigDecimal getTotalPaid(Long invoiceId) {

        BigDecimal totalPaid =
                paymentRepository.getTotalSalesInvoicePayments(invoiceId);

        return safeAmount(totalPaid);
    }

    // ========================================
    // CREDIT CALCULATION
    // ========================================

    private BigDecimal getTotalCreditApplied(Long invoiceId) {

        BigDecimal totalCredit =
                customerCreditTransactionRepository
                        .getNetAppliedCreditForSalesInvoice(
                                invoiceId,
                                SALES_CREDIT_TYPES
                        );

        return safeAmount(totalCredit).max(ZERO);
    }

    // ========================================
    // OUTSTANDING CALCULATION
    // ========================================

    private BigDecimal calculateOutstanding(
            BigDecimal totalAmount,
            BigDecimal totalPaid,
            BigDecimal totalCreditApplied
    ) {
        return safeAmount(totalAmount)
                .subtract(safeAmount(totalPaid))
                .subtract(safeAmount(totalCreditApplied))
                .max(ZERO);
    }

    // ========================================
    // STATUS CALCULATION
    // ========================================

    private SalesInvoiceStatus calculateStatus(
            BigDecimal totalPaid,
            BigDecimal totalCreditApplied,
            BigDecimal outstanding
    ) {
        boolean hasPayment = totalPaid.compareTo(ZERO) > 0;
        boolean hasCredit = totalCreditApplied.compareTo(ZERO) > 0;
        boolean fullySettled = outstanding.compareTo(ZERO) <= 0;

        if (fullySettled) {

            if (hasPayment && hasCredit) {
                return SalesInvoiceStatus.SETTLED;
            }

            if (hasCredit) {
                return SalesInvoiceStatus.CREDITED;
            }

            return SalesInvoiceStatus.PAID;
        }

        if (hasPayment && hasCredit) {
            return SalesInvoiceStatus.PARTIALLY_SETTLED;
        }

        if (hasCredit) {
            return SalesInvoiceStatus.PARTIALLY_CREDITED;
        }

        if (hasPayment) {
            return SalesInvoiceStatus.PARTIALLY_PAID;
        }

        return SalesInvoiceStatus.UNPAID;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : ZERO;
    }

    // ========================================
    // RESPONSE CONVERSION
    // ========================================

    private SalesInvoiceResponse convertToResponse(SalesInvoice invoice) {

        SalesInvoiceResponse response = new SalesInvoiceResponse();

        response.setId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());

        response.setSalesId(invoice.getSales().getId());
        response.setSalesNumber(invoice.getSales().getSalesNumber());

        response.setInvoiceDate(invoice.getInvoiceDate());

        response.setCustomerCode(invoice.getCustomerCode());
        response.setCustomerName(invoice.getCustomerName());
        response.setCustomerPhone(invoice.getCustomerPhone());
        response.setCustomerEmail(invoice.getCustomerEmail());
        response.setCustomerAddress(invoice.getCustomerAddress());

        BigDecimal totalAmount = safeAmount(invoice.getTotalAmount());
        BigDecimal totalPaid = getTotalPaid(invoice.getId());
        BigDecimal totalCreditApplied = getTotalCreditApplied(invoice.getId());

        BigDecimal outstandingAmount = calculateOutstanding(
                totalAmount,
                totalPaid,
                totalCreditApplied
        );

        SalesInvoiceStatus calculatedStatus = calculateStatus(
                totalPaid,
                totalCreditApplied,
                outstandingAmount
        );

        response.setTotalAmount(totalAmount);
        response.setTotalPaid(totalPaid);
        response.setTotalCreditApplied(totalCreditApplied);
        response.setOutstandingAmount(outstandingAmount);
        response.setStatus(calculatedStatus);

        List<SalesInvoiceItemResponse> itemResponses = new ArrayList<>();

        for (SalesInvoiceItem item : invoice.getItems()) {

            SalesInvoiceItemResponse itemResponse =
                    new SalesInvoiceItemResponse();

            itemResponse.setId(item.getId());
            itemResponse.setProductCode(item.getProductCode());
            itemResponse.setProductName(item.getProductName());
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(item.getUnitPrice());
            itemResponse.setSubtotal(item.getSubtotal());

            itemResponses.add(itemResponse);
        }

        response.setItems(itemResponses);

        return response;
    }
}