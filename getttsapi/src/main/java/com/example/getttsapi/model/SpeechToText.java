package com.example.getttsapi.model;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.net.URI;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

public class SpeechToText {

    public static String stt(String apiKey, String filePath) {
        String url = "https://uzbekvoice.ai/api/v1/stt";

        try {
            // Faylni o'qish
            File audioFile = new File(filePath);
            byte[] fileContent = Files.readAllBytes(audioFile.toPath());

            // Multipart form-data uchun boundary
            String boundary = "----FormBoundary" + System.currentTimeMillis();
            byte[] body = buildMultipartBody(fileContent, boundary);

            // HTTP client yaratish
            HttpClient client = HttpClient.newHttpClient();

            // Request yaratish
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

            // Request yuborish
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                return "Request failed with status code " + response.statusCode() + ": " + response.body();
            }

        } catch (java.net.http.HttpTimeoutException e) {
            return "Request timed out. The API response took too long to arrive.";
        } catch (IOException e) {
            return "File reading error: " + e.getMessage();
        } catch (InterruptedException e) {
            return "Request interrupted: " + e.getMessage();
        }
    }

    private static byte[] buildMultipartBody(byte[] fileContent, String boundary) throws IOException {
        String boundaryLine = "--" + boundary + "\r\n";
        String endBoundary = "--" + boundary + "--\r\n";

        StringBuilder sb = new StringBuilder();

        // File part
        sb.append(boundaryLine);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.mp3\"\r\n");
        sb.append("Content-Type: audio/mpeg\r\n\r\n");

        byte[] header = sb.toString().getBytes();
        byte[] footer = ("\r\n" + boundaryLine).getBytes();

        // Data parameters
        String params = "Content-Disposition: form-data; name=\"return_offsets\"\r\n\r\ntrue\r\n" +
                boundaryLine +
                "Content-Disposition: form-data; name=\"run_diarization\"\r\n\r\nfalse\r\n" +
                boundaryLine +
                "Content-Disposition: form-data; name=\"language\"\r\n\r\nuz\r\n" +
                boundaryLine +
                "Content-Disposition: form-data; name=\"model\"\r\n\r\ngeneral\r\n" +
                boundaryLine +
                "Content-Disposition: form-data; name=\"blocking\"\r\n\r\ntrue\r\n" +
                endBoundary;

        byte[] result = new byte[header.length + fileContent.length + footer.length + params.getBytes().length];
        int pos = 0;

        System.arraycopy(header, 0, result, pos, header.length);
        pos += header.length;
        System.arraycopy(fileContent, 0, result, pos, fileContent.length);
        pos += fileContent.length;
        System.arraycopy(footer, 0, result, pos, footer.length);
        pos += footer.length;
        System.arraycopy(params.getBytes(), 0, result, pos, params.getBytes().length);

        return result;
    }

    public static void main(String[] args) {
        String apiKey = "YOUR_API_KEY";
        String filePath = "path/to/audio.mp3";

        String result = stt(apiKey, filePath);
        System.out.println(result);
    }
}