package com.inventory.management.service;

import com.inventory.management.dto.request.PurchaseReturnRequest;
import com.inventory.management.dto.request.PurchaseReturnItemRequest;
import com.inventory.management.dto.response.PurchaseReturnResponse;
import com.inventory.management.dto.response.PurchaseReturnItemResponse;

import com.inventory.management.entity.Purchase;
import com.inventory.management.entity.PurchaseItem;
import com.inventory.management.entity.PurchaseInvoice;
import com.inventory.management.entity.PurchaseReturn;
import com.inventory.management.entity.PurchaseReturnItem;
import com.inventory.management.entity.PurchaseCreditNote;
import com.inventory.management.entity.Supplier;
import com.inventory.management.entity.SupplierCreditTransaction;

import com.inventory.management.enums.PurchaseStatus;
import com.inventory.management.enums.PurchaseInvoiceStatus;
import com.inventory.management.enums.StockMovementType;
import com.inventory.management.enums.CreditNoteStatus;
import com.inventory.management.enums.SupplierCreditTransactionType;

import com.inventory.management.repository.PurchaseRepository;
import com.inventory.management.repository.PurchaseInvoiceRepository;
import com.inventory.management.repository.PurchaseReturnRepository;
import com.inventory.management.repository.PurchaseReturnItemRepository;
import com.inventory.management.repository.PurchaseCreditNoteRepository;
import com.inventory.management.repository.PaymentRepository;
import com.inventory.management.repository.SupplierCreditTransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class PurchaseReturnService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PurchaseRepository purchaseRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnItemRepository purchaseReturnItemRepository;
    private final PurchaseCreditNoteRepository purchaseCreditNoteRepository;
    private final PaymentRepository paymentRepository;
    private final SupplierCreditTransactionRepository supplierCreditTransactionRepository;
    private final InventoryService inventoryService;

    public PurchaseReturnService(
            PurchaseRepository purchaseRepository,
            PurchaseInvoiceRepository purchaseInvoiceRepository,
            PurchaseReturnRepository purchaseReturnRepository,
            PurchaseReturnItemRepository purchaseReturnItemRepository,
            PurchaseCreditNoteRepository purchaseCreditNoteRepository,
            PaymentRepository paymentRepository,
            SupplierCreditTransactionRepository supplierCreditTransactionRepository,
            InventoryService inventoryService
    ) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.purchaseReturnItemRepository = purchaseReturnItemRepository;
        this.purchaseCreditNoteRepository = purchaseCreditNoteRepository;
        this.paymentRepository = paymentRepository;
        this.supplierCreditTransactionRepository = supplierCreditTransactionRepository;
        this.inventoryService = inventoryService;
    }

    public PurchaseReturnResponse createPurchaseReturn(
            PurchaseReturnRequest request
    ) {
        validateRequest(request);

        Purchase purchase = purchaseRepository.findByIdForUpdate(
                request.getPurchaseId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Purchase not found"
        ));

        if (purchase.getStatus() != PurchaseStatus.RECEIVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only received purchases can be returned"
            );
        }

        Supplier supplier = purchase.getSupplier();

        if (supplier == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase supplier is missing"
            );
        }

        PurchaseInvoice foundInvoice = purchaseInvoiceRepository
                .findByPurchase_Id(purchase.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase invoice not found"
                ));

        PurchaseInvoice invoice = purchaseInvoiceRepository
                .findByIdForUpdate(foundInvoice.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase invoice not found"
                ));

        Set<Long> processedItemIds = new HashSet<>();

        PurchaseReturn purchaseReturn = new PurchaseReturn();

        purchaseReturn.setReturnNumber(generateReturnNumber());
        purchaseReturn.setPurchase(purchase);
        purchaseReturn.setPurchaseInvoice(invoice);
        purchaseReturn.setReturnDate(LocalDateTime.now());
        purchaseReturn.setReason(request.getReason());

        BigDecimal totalAmount = ZERO;

        for (PurchaseReturnItemRequest itemRequest : request.getItems()) {

            if (itemRequest == null
                    || itemRequest.getPurchaseItemId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Purchase item ID is required"
                );
            }

            if (itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Return quantity must be greater than zero"
                );
            }

            Long purchaseItemId = itemRequest.getPurchaseItemId();

            if (!processedItemIds.add(purchaseItemId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate purchase item ID in return request"
                );
            }

            PurchaseItem purchaseItem = purchase.getItems()
                    .stream()
                    .filter(item -> item.getId() != null
                            && item.getId().equals(purchaseItemId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Purchase item does not belong to this purchase"
                    ));

            Long previouslyReturned =
                    purchaseReturnItemRepository.getReturnedQuantity(
                            purchaseItemId
                    );

            long returnedQuantity = previouslyReturned == null
                    ? 0
                    : previouslyReturned;

            long requestedTotal = returnedQuantity
                    + itemRequest.getQuantity();

            if (requestedTotal > purchaseItem.getQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Return quantity exceeds original purchase quantity for "
                                + purchaseItem.getProductNameSnapshot()
                );
            }

            if (purchaseItem.getUnitCost() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Original purchase item unit cost is missing"
                );
            }

            if (purchaseItem.getProduct() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product is missing from purchase item"
                );
            }

            BigDecimal unitCost = purchaseItem.getUnitCost();

            BigDecimal subtotal = unitCost.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            PurchaseReturnItem returnItem = new PurchaseReturnItem();

            returnItem.setPurchaseReturn(purchaseReturn);
            returnItem.setPurchaseItem(purchaseItem);
            returnItem.setProductCode(
                    purchaseItem.getProductCodeSnapshot()
            );
            returnItem.setProductName(
                    purchaseItem.getProductNameSnapshot()
            );
            returnItem.setQuantity(itemRequest.getQuantity());
            returnItem.setUnitCost(unitCost);
            returnItem.setSubtotal(subtotal);

            purchaseReturn.getItems().add(returnItem);

            inventoryService.decreaseStock(
                    purchaseItem.getProduct(),
                    itemRequest.getQuantity(),
                    StockMovementType.PURCHASE_RETURN,
                    purchaseReturn.getReturnNumber(),
                    "Purchase return"
            );

            totalAmount = totalAmount.add(subtotal);
        }

        purchaseReturn.setTotalAmount(totalAmount);

        PurchaseReturn savedReturn =
                purchaseReturnRepository.saveAndFlush(purchaseReturn);

        PurchaseCreditNote creditNote = new PurchaseCreditNote();

        creditNote.setCreditNoteNumber(generateCreditNoteNumber());
        creditNote.setPurchaseReturn(savedReturn);
        creditNote.setPurchaseInvoice(invoice);
        creditNote.setTotalAmount(totalAmount);
        creditNote.setStatus(CreditNoteStatus.ISSUED);

        PurchaseCreditNote savedCreditNote =
                purchaseCreditNoteRepository.saveAndFlush(creditNote);

        if (supplierCreditTransactionRepository
                .existsByPurchaseCreditNoteId(savedCreditNote.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Credit transaction already exists for this credit note"
            );
        }

        SupplierCreditTransaction creditTransaction =
                new SupplierCreditTransaction();

        creditTransaction.setSupplier(supplier);
        creditTransaction.setPurchaseCreditNote(savedCreditNote);
        creditTransaction.setPurchaseInvoice(invoice);

        creditTransaction.setTransactionType(
                SupplierCreditTransactionType.CREDIT_NOTE_ISSUED
        );

        creditTransaction.setAmount(totalAmount);
        creditTransaction.setReferenceNumber(
                savedCreditNote.getCreditNoteNumber()
        );

        creditTransaction.setRemarks(
                "Supplier credit issued from purchase return "
                        + savedReturn.getReturnNumber()
        );

        supplierCreditTransactionRepository.saveAndFlush(
                creditTransaction
        );

        updatePurchaseInvoiceStatus(invoice);

        return convertToResponse(savedReturn);
    }

    private void validateRequest(PurchaseReturnRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Purchase return request is required"
            );
        }

        if (request.getPurchaseId() == null
                || request.getPurchaseId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid purchase ID is required"
            );
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one return item is required"
            );
        }
    }

    private void updatePurchaseInvoiceStatus(PurchaseInvoice invoice) {

        BigDecimal total = safeAmount(invoice.getTotalAmount());

        BigDecimal paid = safeAmount(
                paymentRepository.getTotalPurchaseInvoicePayments(
                        invoice.getId()
                )
        );

        List<SupplierCreditTransaction> transactions =
                supplierCreditTransactionRepository
                        .findByPurchaseInvoice_IdOrderByCreatedAtDesc(
                                invoice.getId()
                        );

        BigDecimal appliedCredit =
                calculateNetAppliedCredit(transactions);

        BigDecimal remaining = total
                .subtract(paid)
                .subtract(appliedCredit)
                .max(ZERO);

        invoice.setStatus(
                determineStatus(
                        total,
                        paid,
                        appliedCredit,
                        remaining
                )
        );

        purchaseInvoiceRepository.save(invoice);
    }

    private BigDecimal calculateNetAppliedCredit(
            List<SupplierCreditTransaction> transactions
    ) {
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

    private PurchaseInvoiceStatus determineStatus(
            BigDecimal total,
            BigDecimal paid,
            BigDecimal credit,
            BigDecimal remaining
    ) {
        if (remaining.compareTo(ZERO) > 0) {

            if (paid.compareTo(ZERO) > 0
                    && credit.compareTo(ZERO) > 0) {
                return PurchaseInvoiceStatus.PARTIALLY_SETTLED;
            }

            if (paid.compareTo(ZERO) > 0) {
                return PurchaseInvoiceStatus.PARTIALLY_PAID;
            }

            if (credit.compareTo(ZERO) > 0) {
                return PurchaseInvoiceStatus.PARTIALLY_CREDITED;
            }

            return PurchaseInvoiceStatus.UNPAID;
        }

        if (paid.compareTo(total) >= 0) {
            return PurchaseInvoiceStatus.PAID;
        }

        if (credit.compareTo(total) >= 0) {
            return PurchaseInvoiceStatus.CREDITED;
        }

        return PurchaseInvoiceStatus.SETTLED;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse getPurchaseReturnById(Long id) {

        PurchaseReturn purchaseReturn = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase return not found"
                ));

        return convertToResponse(purchaseReturn);
    }

    @Transactional(readOnly = true)
    public List<PurchaseReturnResponse> getPurchaseReturnsByPurchaseId(
            Long purchaseId
    ) {
        return purchaseReturnRepository
                .findByPurchase_IdOrderByReturnDateDesc(purchaseId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    private PurchaseReturnResponse convertToResponse(
            PurchaseReturn purchaseReturn
    ) {
        PurchaseReturnResponse response = new PurchaseReturnResponse();

        response.setId(purchaseReturn.getId());
        response.setReturnNumber(purchaseReturn.getReturnNumber());
        response.setPurchaseId(purchaseReturn.getPurchase().getId());
        response.setPurchaseNumber(
                purchaseReturn.getPurchase().getPurchaseNumber()
        );
        response.setPurchaseInvoiceId(
                purchaseReturn.getPurchaseInvoice().getId()
        );
        response.setInvoiceNumber(
                purchaseReturn.getPurchaseInvoice().getInvoiceNumber()
        );
        response.setReturnDate(purchaseReturn.getReturnDate());
        response.setReason(purchaseReturn.getReason());
        response.setTotalAmount(purchaseReturn.getTotalAmount());

        List<PurchaseReturnItemResponse> items =
                purchaseReturn.getItems()
                        .stream()
                        .map(item -> {
                            PurchaseReturnItemResponse itemResponse =
                                    new PurchaseReturnItemResponse();

                            itemResponse.setId(item.getId());
                            itemResponse.setPurchaseItemId(
                                    item.getPurchaseItem().getId()
                            );
                            itemResponse.setProductCode(
                                    item.getProductCode()
                            );
                            itemResponse.setProductName(
                                    item.getProductName()
                            );
                            itemResponse.setQuantity(item.getQuantity());
                            itemResponse.setUnitCost(item.getUnitCost());
                            itemResponse.setSubtotal(item.getSubtotal());

                            return itemResponse;
                        })
                        .toList();

        response.setItems(items);

        return response;
    }

    private String generateReturnNumber() {
        return "PRT-"
                + LocalDateTime.now().getYear()
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private String generateCreditNoteNumber() {
        return "PCN-"
                + LocalDateTime.now().getYear()
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}