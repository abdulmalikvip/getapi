package com.example.getttsapi.dto;

public class TtsRequest {
    private String text;
    private String model;  // ← "lola" yoki boshqa model
    private boolean blocking;
    private String webhook_notification_url;

    // Eski parametrlar (ixtiyoriy, kompatibillik uchun)
    private String language;
    private String speaker;

    public TtsRequest() {}

    public TtsRequest(String text, String model, boolean blocking) {
        this.text = text;
        this.model = model;
        this.blocking = blocking;
    }

    // Getters va Setters
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isBlocking() { return blocking; }
    public void setBlocking(boolean blocking) { this.blocking = blocking; }

    public String getWebhook_notification_url() { return webhook_notification_url; }
    public void setWebhook_notification_url(String webhook_notification_url) {
        this.webhook_notification_url = webhook_notification_url;
    }

    // Eski parametrlar (kompatibillik)
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }
}