package com.example.getttsapi.model;


import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "speech_records")
public class SpeechRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId; // Uzbekvoice API dan kelgan ID

    @Column(name = "text", length = 5000)
    private String text;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "type") // "STT" yoki "TTS"
    private String type;

    @Column(name = "language")
    private String language;

    @Column(name = "status")
    private String status; // "SUCCESS", "PENDING", "FAILED"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public SpeechRecord() {}

    public SpeechRecord(String externalId, String text, String type, String language) {
        this.externalId = externalId;
        this.text = text;
        this.type = type;
        this.language = language;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}