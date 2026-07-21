package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.BookingDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingDraftRepository extends JpaRepository<BookingDraft, String> {
}
