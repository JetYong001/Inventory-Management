package com.inventory.management.service;

import com.inventory.management.dto.response.SalesInvoiceItemResponse;
import com.inventory.management.dto.response.SalesInvoiceResponse;
import com.inventory.management.entity.Customer;
import com.inventory.management.entity.Sales;
import com.inventory.management.entity.SalesItem;
import com.inventory.management.entity.SalesInvoice;
import com.inventory.management.entity.SalesInvoiceItem;
import com.inventory.management.enums.SalesInvoiceStatus;
import com.inventory.management.exception.InvalidSalesOperationException;
import com.inventory.management.exception.SalesInvoiceNotFoundException;
import com.inventory.management.repository.SalesInvoiceRepository;
import com.inventory.management.repository.SalesRepository;
import com.inventory.management.util.BusinessCodeGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SalesInvoiceService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesRepository salesRepository;

    public SalesInvoiceService(
            SalesInvoiceRepository salesInvoiceRepository,
            SalesRepository salesRepository) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.salesRepository = salesRepository;
    }

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

            if (productCode == null || productName == null) {
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

    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getAllInvoices() {

        return salesInvoiceRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalesInvoiceResponse getInvoiceById(Long invoiceId) {

        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() ->
                        new SalesInvoiceNotFoundException(
                                "Sales invoice not found: " + invoiceId
                        ));

        return convertToResponse(invoice);
    }

    @Transactional(readOnly = true)
    public SalesInvoiceResponse getInvoiceBySalesId(Long salesId) {

        SalesInvoice invoice = salesInvoiceRepository.findBySales_Id(salesId)
                .orElseThrow(() ->
                        new SalesInvoiceNotFoundException(
                                "Invoice not found for sales ID: " + salesId
                        ));

        return convertToResponse(invoice);
    }

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

        response.setTotalAmount(invoice.getTotalAmount());
        response.setStatus(invoice.getStatus());

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