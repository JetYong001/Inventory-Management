
package com.inventory.management.entity;

import com.inventory.management.enums.PaymentMethod;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_refunds",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customer_refund_number",
                        columnNames = "refund_number"
                )
        }
)
public class CustomerRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_number", nullable = false, length = 50)
    private String refundNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDate refundDate;

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CustomerRefund() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (refundDate == null) {
            refundDate = LocalDate.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRefundNumber() {
        return refundNumber;
    }

    public void setRefundNumber(String refundNumber) {
        this.refundNumber = refundNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDate getRefundDate() {
        return refundDate;
    }

    public void setRefundDate(LocalDate refundDate) {
        this.refundDate = refundDate;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
