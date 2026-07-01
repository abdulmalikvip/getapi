package com.example.getttsapi.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;

@Service
public class UzbekVoiceApiClient {

    @Value("${uzbekvoice.api.key}")
    private String apiKey;

    @Value("${uzbekvoice.api.url:https://uzbekvoice.ai/api/v1}")
    private String apiUrl;

    /**
     * Speech to Text - Nutqni matga aylantirish (STT)
     */
    public JsonObject speechToText(String filePath, String language, String model,
                                   boolean returnOffsets, boolean blocking) {
        String url = apiUrl + "/stt";

        System.out.println("========== STT API CLIENT DEBUG ==========");
        System.out.println("🔗 URL: " + url);
        System.out.println("📝 File: " + filePath);
        System.out.println("🌐 Language: " + language);
        System.out.println("🎵 Model: " + model);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(url);
            uploadFile.setHeader("Authorization", apiKey);

            File file = new File(filePath);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", file,
                    org.apache.http.entity.ContentType.create("audio/mpeg"), "audio.mp3");
            builder.addTextBody("return_offsets", String.valueOf(returnOffsets));
            builder.addTextBody("run_diarization", "false");
            builder.addTextBody("language", language);
            builder.addTextBody("model", model);
            builder.addTextBody("blocking", String.valueOf(blocking));

            uploadFile.setEntity(builder.build());

            System.out.println("🚀 Sending STT request...");
            org.apache.http.HttpResponse response = httpClient.execute(uploadFile);
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println("📥 Status Code: " + statusCode);
            System.out.println("📥 Response: " + responseBody);
            System.out.println("==========================================\n");

            if (statusCode == 200) {
                return JsonParser.parseString(responseBody).getAsJsonObject();
            } else {
                JsonObject error = new JsonObject();
                error.addProperty("error", "STT API Error");
                error.addProperty("statusCode", statusCode);
                error.addProperty("message", responseBody);
                return error;
            }

        } catch (Exception e) {
            System.err.println("❌ STT API ERROR: " + e.getMessage());
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("error", "Exception");
            error.addProperty("message", e.getMessage());
            return error;
        }
    }

    /**
     * Text to Speech - Matni nutqqa aylantirish (TTS)
     */
    public JsonObject textToSpeechWithModel(String text, String model, boolean blocking) {
        String url = apiUrl + "/tts";

        System.out.println("========== UZBEKVOICE TTS DEBUG ==========");
        System.out.println("🔗 URL: " + url);
        System.out.println("📝 Text: " + text);
        System.out.println("🎵 Model: " + model);
        System.out.println("⏱️  Blocking: " + blocking);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(url);
            postRequest.setHeader("Authorization", apiKey);
            postRequest.setHeader("Content-Type", "application/json; charset=UTF-8");

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("text", text);
            jsonBody.addProperty("model", model);
            jsonBody.addProperty("blocking", blocking);

            System.out.println("📤 Request Body: " + jsonBody.toString());

            // ✅ UTF-8 encoding bilan StringEntity yaratish
            postRequest.setEntity(new org.apache.http.entity.StringEntity(
                    jsonBody.toString(),
                    StandardCharsets.UTF_8
            ));

            System.out.println("🚀 Sending TTS request...");
            org.apache.http.HttpResponse response = httpClient.execute(postRequest);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            System.out.println("📥 Status Code: " + statusCode);
            System.out.println("📥 Response Body: " + responseBody);
            System.out.println("==========================================\n");

            if (statusCode == 200) {
                return JsonParser.parseString(responseBody).getAsJsonObject();
            } else {
                JsonObject error = new JsonObject();
                error.addProperty("error", "TTS API Error");
                error.addProperty("statusCode", statusCode);
                error.addProperty("message", responseBody);
                return error;
            }

        } catch (Exception e) {
            System.err.println("❌ TTS EXCEPTION: " + e.getMessage());
            e.printStackTrace();

            JsonObject error = new JsonObject();
            error.addProperty("error", "Exception");
            error.addProperty("message", e.getMessage());
            return error;
        }
    }

    /**
     * TTS Status tekshirish (Polling uchun)
     * ✅ TUZATILGAN: ID format yo'l bo'yicha to'g'ri ko'rsatiladi
     */
    public JsonObject checkTtsStatus(String ttsId) {
        System.out.println("🔄 Raw TTS ID from API: " + ttsId);

        // API response ID'da "tts/" prefix bor: tts/UUID/UUID
        // Status check endpoint: /tts/{id} shaklida FAQAT ID qismi kerak
        // Agar "tts/" prefix bor bo'lsa, uni kesamiz
        String cleanId = ttsId;
        if (ttsId.startsWith("tts/")) {
            cleanId = ttsId.substring(4); // "tts/" o'rnini kesish -> UUID/UUID
            System.out.println("✂️  Removed 'tts/' prefix");
        }

        String url = apiUrl + "/tts/" + cleanId;

        System.out.println("🔄 Checking TTS status");
        System.out.println("📌 Original ID: " + ttsId);
        System.out.println("📌 Clean ID: " + cleanId);
        System.out.println("🔗 Full URL: " + url);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(url);
            getRequest.setHeader("Authorization", apiKey);

            org.apache.http.HttpResponse response = httpClient.execute(getRequest);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            System.out.println("📥 Status Code: " + statusCode);
            System.out.println("📥 Response: " + responseBody);

            if (statusCode == 200) {
                return JsonParser.parseString(responseBody).getAsJsonObject();
            } else {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Status check failed");
                error.addProperty("statusCode", statusCode);
                error.addProperty("message", responseBody);
                return error;
            }

        } catch (Exception e) {
            System.err.println("❌ Status check error: " + e.getMessage());
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            return error;
        }
    }
}
