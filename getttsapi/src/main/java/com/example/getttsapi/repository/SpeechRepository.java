package com.example.getttsapi.repository;


import com.example.getttsapi.model.SpeechRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpeechRepository extends JpaRepository<SpeechRecord, Long> {
    Optional<SpeechRecord> findByExternalId(String externalId);
}