package com.example.getttsapi.service;

import com.example.getttsapi.dto.TtsRequest;
import com.example.getttsapi.dto.TtsResponse;
import com.example.getttsapi.dto.SttResponse;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class BotService {

    @Autowired
    private UzbekVoiceApiClient uzbekVoiceApiClient;

    @Autowired
    private SpeechService speechService;

    /**
     * Ovoz faylini matnga aylantirish (STT) - Bot uchun string path versiyasi
     */
    public SttResponse convertVoiceToText(String voiceFilePath) {
        try {
            System.out.println("========== BOT STT REQUEST STARTED ==========");
            System.out.println("📁 Voice file path: " + voiceFilePath);

            File voiceFile = new File(voiceFilePath);
            System.out.println("📂 File exists: " + voiceFile.exists());
            System.out.println("📊 File size: " + voiceFile.length() + " bytes");

            if (!voiceFile.exists()) {
                throw new RuntimeException("Fayl topilmadi: " + voiceFilePath);
            }

            // API ga soʻrov yuborish
            System.out.println("🔌 Calling Uzbekvoice API...");
            JsonObject apiResponse = uzbekVoiceApiClient.speechToText(
                    voiceFilePath,
                    "uz",      // til
                    "general", // model
                    false,     // returnOffsets
                    true       // blocking
            );

            System.out.println("📨 API Response: " + apiResponse.toString());

            // Javobni tahlil qilish
            String extractedText = "";
            String externalId = "";

            if (apiResponse.has("error")) {
                System.err.println("❌ API Error: " + apiResponse.get("error").getAsString());
                throw new RuntimeException("API Error: " + apiResponse.get("error").getAsString());
            }

            if (apiResponse.has("result") && apiResponse.get("result").isJsonObject()) {
                JsonObject result = apiResponse.get("result").getAsJsonObject();
                if (result.has("text")) {
                    extractedText = result.get("text").getAsString();
                    System.out.println("✅ Text extracted: " + extractedText);
                }
            }

            if (apiResponse.has("id")) {
                externalId = apiResponse.get("id").getAsString();
            }

            // Databasega saqlash
            if (!extractedText.isEmpty()) {
                speechService.saveSTTRecord("STT", "uz", extractedText, externalId);
            }

            System.out.println("💾 Record saved to DB");
            System.out.println("========== BOT STT REQUEST COMPLETED ==========\n");

            return new SttResponse(externalId, extractedText, "SUCCESS");

        } catch (Exception e) {
            System.err.println("❌ BOT STT ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("STT xatosi: " + e.getMessage());
        }
    }

    /**
     * Matni nutqqa aylantirish (TTS)
     */
    public String convertTextToSpeech(TtsRequest ttsRequest) {
        try {
            System.out.println("========== BOT TTS REQUEST STARTED ==========");
            System.out.println("📝 Text: " + ttsRequest.getText());
            System.out.println("🎵 Model: " + (ttsRequest.getModel() != null ? ttsRequest.getModel() : "lola"));

            String model = ttsRequest.getModel() != null ? ttsRequest.getModel() : "lola";
            boolean blocking = ttsRequest.isBlocking();

            // Uzbekvoice API ga TTS soʻrovi yuborish
            JsonObject apiResponse = uzbekVoiceApiClient.textToSpeechWithModel(
                    ttsRequest.getText(),
                    model,
                    blocking
            );

            System.out.println("📨 Initial API Response: " + apiResponse.toString());

            String audioUrl = null;
            String ttsId = null;

            // Error tekshirish
            if (apiResponse.has("error")) {
                System.err.println("❌ API Error: " + apiResponse.get("error").getAsString());
                throw new RuntimeException("TTS API Error: " + apiResponse.get("error").getAsString());
            }

            // Darhol javob olish
            if (apiResponse.has("result") && apiResponse.get("result").isJsonObject()) {
                JsonObject result = apiResponse.get("result").getAsJsonObject();
                if (result.has("url")) {
                    audioUrl = result.get("url").getAsString();
                    System.out.println("✅ Audio URL received immediately: " + audioUrl);
                }
            }

            if (apiResponse.has("id")) {
                ttsId = apiResponse.get("id").getAsString();
                System.out.println("📌 TTS ID: " + ttsId);
            }

            // Agar darhol URL olmasa, polling qilish
            if (audioUrl == null && ttsId != null) {
                System.out.println("⏳ Polling for TTS completion...");
                audioUrl = pollForTtsCompletion(ttsId);
            }

            if (audioUrl == null || audioUrl.isEmpty()) {
                System.err.println("❌ No audio URL obtained");
                throw new RuntimeException("Audio URL sini olishda xato");
            }

            System.out.println("✅ Final Audio URL: " + audioUrl);
            System.out.println("========== BOT TTS REQUEST COMPLETED ==========\n");

            return audioUrl;

        } catch (Exception e) {
            System.err.println("❌ BOT TTS ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("TTS xatosi: " + e.getMessage());
        }
    }

    /**
     * TTS yakunlanishini kutish (polling)
     */
    private String pollForTtsCompletion(String ttsId) {
        System.out.println("⏳ Starting TTS polling for ID: " + ttsId);
        int maxAttempts = 30; // 30 ta urinish
        int delayMs = 4000;   // 4 soniya oralig'i (jami 2 minut)

        for (int i = 0; i < maxAttempts; i++) {
            try {
                System.out.println("⏳ Attempt " + (i + 1) + "/" + maxAttempts);
                Thread.sleep(delayMs);

                // Statusni tekshirish
                JsonObject statusResponse = uzbekVoiceApiClient.checkTtsStatus(ttsId);

                if (statusResponse.has("error")) {
                    System.err.println("⚠️ API Error: " + statusResponse.get("error").getAsString());
                    continue;
                }

                if (statusResponse.has("status")) {
                    String status = statusResponse.get("status").getAsString();
                    System.out.println("📊 Status: " + status);

                    if ("SUCCESS".equals(status) || "COMPLETED".equals(status)) {
                        if (statusResponse.has("result") && statusResponse.get("result").isJsonObject()) {
                            JsonObject result = statusResponse.get("result").getAsJsonObject();
                            if (result.has("url")) {
                                String audioUrl = result.get("url").getAsString();
                                System.out.println("✅ Audio URL obtained after " + (i + 1) + " attempts");
                                return audioUrl;
                            }
                        }
                    } else if ("PROGRESS".equals(status) || "PROCESSING".equals(status) || "IN_PROGRESS".equals(status)) {
                        System.out.println("🔄 Still processing...");
                        continue;
                    } else if ("FAILED".equals(status)) {
                        throw new RuntimeException("TTS processing failed");
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("❌ Polling interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("⚠️ Polling error on attempt " + (i + 1) + ": " + e.getMessage());
            }
        }

        System.err.println("❌ TTS polling timeout after " + maxAttempts + " attempts");
        throw new RuntimeException("Audio yaratish vaqti tugadi. Qayta urinib ko'ring.");
    }
}
