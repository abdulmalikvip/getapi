package com.example.getttsapi.dto;

public class SttRequest {
    private String file; // Base64 yoki fayl yili
    private boolean returnOffsets;
    private boolean runDiarization;
    private String language; // "uz", "ru", "uz-ru"
    private String model; // "general", "enhanced-stt"
    private boolean blocking;
    private String webhookUrl;

    // Constructors
    public SttRequest() {}

    public SttRequest(String file, boolean returnOffsets, String language, String model) {
        this.file = file;
        this.returnOffsets = returnOffsets;
        this.language = language;
        this.model = model;
        this.blocking = true;
        this.runDiarization = false;
    }

    // Getters and Setters
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public boolean isReturnOffsets() { return returnOffsets; }
    public void setReturnOffsets(boolean returnOffsets) { this.returnOffsets = returnOffsets; }

    public boolean isRunDiarization() { return runDiarization; }
    public void setRunDiarization(boolean runDiarization) { this.runDiarization = runDiarization; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isBlocking() { return blocking; }
    public void setBlocking(boolean blocking) { this.blocking = blocking; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
}