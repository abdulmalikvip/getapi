package com.example.getttsapi.dto;


public class TtsResponse {
    private String audioUrl; // Audio fayl URL
    private String format; // "mp3", "wav"
    private boolean success;

    public TtsResponse() {}

    public TtsResponse(String audioUrl, String format, boolean success) {
        this.audioUrl = audioUrl;
        this.format = format;
        this.success = success;
    }

    // Getters and Setters
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}