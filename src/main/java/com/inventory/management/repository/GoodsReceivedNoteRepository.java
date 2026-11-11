package com.inventory.management.repository;

import com.inventory.management.entity.GoodsReceivedNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoodsReceivedNoteRepository
        extends JpaRepository<GoodsReceivedNote, Long> {

    Optional<GoodsReceivedNote> findByRequestKey(String requestKey);

    List<GoodsReceivedNote> findAllByPurchase_IdOrderByReceivedAtDesc(
            Long purchaseId
    );
}