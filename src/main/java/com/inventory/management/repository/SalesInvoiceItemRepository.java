
package com.inventory.management.repository;

import com.inventory.management.entity.SalesInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesInvoiceItemRepository
        extends JpaRepository<SalesInvoiceItem, Long> {

    List<SalesInvoiceItem> findAllByInvoice_Id(Long invoiceId);
}
