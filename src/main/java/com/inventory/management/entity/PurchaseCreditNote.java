package com.inventory.management.entity;

import com.inventory.management.enums.CreditNoteStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "purchase_credit_notes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_purchase_credit_note_number",
                        columnNames = "credit_note_number"
                ),
                @UniqueConstraint(
                        name = "uk_purchase_credit_note_return",
                        columnNames = "purchase_return_id"
                )
        }
)
public class PurchaseCreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_note_number", nullable = false, length = 30)
    private String creditNoteNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_id", nullable = false)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    private PurchaseInvoice purchaseInvoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditNoteStatus status = CreditNoteStatus.ISSUED;

    @Column(nullable = false)
    private LocalDateTime creditNoteDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (creditNoteDate == null) {
            creditNoteDate = now;
        }

        createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public void setCreditNoteNumber(String creditNoteNumber) {
        this.creditNoteNumber = creditNoteNumber;
    }

    public PurchaseReturn getPurchaseReturn() {
        return purchaseReturn;
    }

    public void setPurchaseReturn(PurchaseReturn purchaseReturn) {
        this.purchaseReturn = purchaseReturn;
    }

    public PurchaseInvoice getPurchaseInvoice() {
        return purchaseInvoice;
    }

    public void setPurchaseInvoice(PurchaseInvoice purchaseInvoice) {
        this.purchaseInvoice = purchaseInvoice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public CreditNoteStatus getStatus() {
        return status;
    }

    public void setStatus(CreditNoteStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreditNoteDate() {
        return creditNoteDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}