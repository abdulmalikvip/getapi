package com.example.getttsapi.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.example.getttsapi.dto.TtsRequest;
import com.example.getttsapi.dto.SttResponse;
import com.example.getttsapi.service.BotService;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

@Component
public class MyBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Autowired
    private BotService botService;

    private Map<Long, String> userStates = new HashMap<>();

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                var message = update.getMessage();
                long chatId = message.getChatId();

                // Text komandalarini boshqarish
                if (message.hasText()) {
                    String text = message.getText();
                    handleTextMessage(chatId, text);
                }
                // Ovoz xabarlarini boshqarish
                else if (message.hasVoice()) {
                    System.out.println("🎤 VOICE MESSAGE RECEIVED");
                    handleVoiceMessage(chatId, message.getVoice().getFileId());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error in onUpdateReceived: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleTextMessage(long chatId, String text) {
        try {
            System.out.println("📝 TEXT MESSAGE: " + text);

            if (text.equals("/start")) {
                sendStartMessage(chatId);
                userStates.put(chatId, "STARTED");
            } else if (text.equals("/help")) {
                sendHelpMessage(chatId);
            } else if (text.equals("📝 Matnga o'tkazish (STT)")) {
                userStates.put(chatId, "WAITING_FOR_VOICE");
                sendMessage(chatId, "🎤 Iltimos, ovoz xabarini yuboring...");
            } else if (text.equals("🎵 Nutqqa o'tkazish (TTS)")) {
                userStates.put(chatId, "WAITING_FOR_TEXT");
                sendMessage(chatId, "📝 Iltimos, matnni yuboring...");
            } else if (text.equals("/cancel")) {
                userStates.put(chatId, "STARTED");
                sendStartMessage(chatId);
            } else if (userStates.getOrDefault(chatId, "").equals("WAITING_FOR_TEXT")) {
                System.out.println("🔊 TTS REQUEST: " + text);
                handleTextToSpeech(chatId, text);
            } else {
                sendMessage(chatId, "❓ /start bilan boshlang");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in handleTextMessage: " + e.getMessage());
            e.printStackTrace();
            sendMessage(chatId, "❌ Xato: " + e.getMessage());
        }
    }

    private void handleVoiceMessage(long chatId, String fileId) {
        try {
            String state = userStates.getOrDefault(chatId, "");
            System.out.println("🎤 Voice state: " + state);

            if (!state.equals("WAITING_FOR_VOICE")) {
                sendMessage(chatId, "❌ Avval STT tugmasini bosing");
                return;
            }

            sendMessage(chatId, "⏳ Qayta ishlanmoqda...");

            System.out.println("📥 Downloading voice file: " + fileId);
            String downloadedFilePath = downloadVoiceFile(fileId);

            if (downloadedFilePath == null || downloadedFilePath.isEmpty()) {
                System.err.println("❌ Download failed");
                sendMessage(chatId, "❌ Faylni yuklashda xato");
                return;
            }

            System.out.println("✅ File downloaded: " + downloadedFilePath);

            try {
                System.out.println("🔌 Calling BotService.convertVoiceToText...");
                SttResponse response = botService.convertVoiceToText(downloadedFilePath);

                System.out.println("✅ STT Response received: " + (response != null ? response.getText() : "null"));

                if (response != null && response.getText() != null && !response.getText().isEmpty()) {
                    sendMessage(chatId, "✅ Natija:\n\n" + response.getText());
                } else {
                    sendMessage(chatId, "❌ Javob bo'sh. API xatosi.");
                }
            } catch (Exception e) {
                System.err.println("❌ BotService error: " + e.getMessage());
                e.printStackTrace();
                sendMessage(chatId, "❌ STT xatosi: " + e.getMessage());
            }

            // Faylni o'chirish
            java.io.File tempFile = new java.io.File(downloadedFilePath);
            if (tempFile.exists()) {
                tempFile.delete();
            }

        } catch (Exception e) {
            System.err.println("❌ Voice processing error: " + e.getMessage());
            e.printStackTrace();
            sendMessage(chatId, "❌ Xato: " + e.getMessage());
        } finally {
            userStates.put(chatId, "STARTED");
            try {
                sendStartMessage(chatId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTextToSpeech(long chatId, String text) {
        try {
            System.out.println("🎵 TTS REQUEST STARTED: " + text);

            if (text.length() < 2) {
                sendMessage(chatId, "❌ Matn qisqa");
                return;
            }

            if (text.length() > 1000) {
                sendMessage(chatId, "❌ Matn juda uzun (max 1000)");
                return;
            }

            sendMessage(chatId, "⏳ Audio yaratilmoqda... Iltimos kuting");

            TtsRequest ttsRequest = new TtsRequest();
            ttsRequest.setText(text);
            ttsRequest.setModel("lola");
            ttsRequest.setBlocking(true);

            try {
                System.out.println("🔌 Calling BotService.convertTextToSpeech...");
                String audioUrl = botService.convertTextToSpeech(ttsRequest);

                System.out.println("✅ TTS Response: " + audioUrl);

                if (audioUrl != null && !audioUrl.isEmpty()) {
                    System.out.println("📤 Sending audio to Telegram...");
                    sendAudio(chatId, audioUrl, text);
                    System.out.println("✅ Audio sent");
                } else {
                    System.err.println("❌ audioUrl null yoki empty");
                    sendMessage(chatId, "❌ Audio yaratilmadi");
                }
            } catch (Exception e) {
                System.err.println("❌ BotService TTS error: " + e.getMessage());
                e.printStackTrace();
                sendMessage(chatId, "❌ TTS xatosi: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("❌ TTS error: " + e.getMessage());
            e.printStackTrace();
            sendMessage(chatId, "❌ Xato: " + e.getMessage());
        } finally {
            userStates.put(chatId, "STARTED");
            try {
                sendStartMessage(chatId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendStartMessage(long chatId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("👋 Assalomu aleykum! TTS/STT botiman.\n\n" +
                    "📌 Matn → Audio\n" +
                    "🎤 Ovoz → Matn");

            ReplyKeyboardMarkup keyboardMarkup = getMainKeyboard();
            message.setReplyMarkup(keyboardMarkup);

            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ SendMessage error: " + e.getMessage());
        }
    }

    private void sendHelpMessage(long chatId) {
        try {
            String helpText = "ℹ️ Botni ishlatish:\n\n" +
                    "1️⃣ STT: Tugma bosing → Ovoz yuborish\n" +
                    "2️⃣ TTS: Tugma bosing → Matn yuborish\n\n" +
                    "Chegaralar:\n" +
                    "• Matn: 2-1000 belgi\n" +
                    "• Audio: 50MB gacha";
            sendMessage(chatId, helpText);
        } catch (Exception e) {
            System.err.println("Error sending help: " + e.getMessage());
        }
    }

    private void sendMessage(long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ SendMessage error: " + e.getMessage());
        }
    }

    private void sendAudio(long chatId, String audioUrl, String caption) {
        try {
            System.out.println("📤 Sending audio: " + audioUrl);
            SendAudio audio = new SendAudio();
            audio.setChatId(chatId);
            audio.setAudio(new InputFile(audioUrl));
            String shortCaption = caption.length() > 50 ? caption.substring(0, 50) + "..." : caption;
            audio.setCaption("🎵 " + shortCaption);

            execute(audio);
            System.out.println("✅ Audio sent successfully");
        } catch (TelegramApiException e) {
            System.err.println("❌ SendAudio error: " + e.getMessage());
            e.printStackTrace();
            sendMessage(chatId, "❌ Audio yuborish xatosi: " + e.getMessage());
        }
    }

    private String downloadVoiceFile(String fileId) {
        try {
            System.out.println("📥 Starting download for fileId: " + fileId);
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);

            org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFileMethod);
            String filePath = telegramFile.getFilePath();
            System.out.println("📂 Telegram path: " + filePath);

            String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
            System.out.println("🔗 Download URL: " + fileUrl);

            URL url = new URL(fileUrl);
            URLConnection connection = url.openConnection();
            InputStream inputStream = connection.getInputStream();

            String tempPath = System.getProperty("java.io.tmpdir") + java.io.File.separator + System.currentTimeMillis() + ".ogg";
            FileOutputStream outputStream = new FileOutputStream(tempPath);

            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            outputStream.close();
            inputStream.close();

            java.io.File tempFile = new java.io.File(tempPath);
            System.out.println("✅ Downloaded: " + tempPath + " (" + totalBytes + " bytes)");

            return tempPath;
        } catch (Exception e) {
            System.err.println("❌ Download error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ReplyKeyboardMarkup getMainKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📝 Matnga o'tkazish (STT)");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🎵 Nutqqa o'tkazish (TTS)");
        keyboard.add(row2);

        KeyboardRow row3 = new KeyboardRow();
        row3.add("/help");
        row3.add("/cancel");
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}
