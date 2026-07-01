package com.example.getttsapi.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.objects.File;
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

    private Map<Long, String> userStates = new HashMap<>(); // Track user state
    private Map<Long, String> userLanguages = new HashMap<>(); // Track user language

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
        if (update.hasMessage()) {
            var message = update.getMessage();
            long chatId = message.getChatId();

            // Handle text commands
            if (message.hasText()) {
                String text = message.getText();
                handleTextMessage(chatId, text);
            }
            // Handle voice messages (Audio)
            else if (message.hasVoice()) {
                handleVoiceMessage(chatId, message.getVoice().getFileId());
            }
        }
    }

    private void handleTextMessage(long chatId, String text) {
        if (text.equals("/start")) {
            sendStartMessage(chatId);
            userStates.put(chatId, "STARTED");
        } else if (text.equals("/help")) {
            sendHelpMessage(chatId);
        } else if (text.equals("📝 Matnga o'tkazish (STT)")) {
            userStates.put(chatId, "WAITING_FOR_VOICE");
            sendMessage(chatId, "🎤 Iltimos, ovoz xabarini yuboring yoki /cancel bilan bekor qiling");
        } else if (text.equals("🎵 Nutqqa o'tkazish (TTS)")) {
            userStates.put(chatId, "WAITING_FOR_TEXT");
            sendMessage(chatId, "📝 Iltimos, matni yuboring yoki /cancel bilan bekor qiling");
        } else if (text.equals("/cancel")) {
            userStates.put(chatId, "STARTED");
            sendMessage(chatId, "❌ Operatsiya bekor qilindi. Asosiy menyu:");
            sendStartMessage(chatId);
        } else if (userStates.getOrDefault(chatId, "").equals("WAITING_FOR_TEXT")) {
            // User sent text for TTS conversion
            handleTextToSpeech(chatId, text);
        } else {
            sendMessage(chatId, "❓ Noto'g'ri buyruq. /start bilan boshlang yoki /help ni o'qing");
        }
    }

    private void handleVoiceMessage(long chatId, String fileId) {
        String state = userStates.getOrDefault(chatId, "");

        if (!state.equals("WAITING_FOR_VOICE")) {
            sendMessage(chatId, "❌ Avval \"📝 Matnga o'tkazish (STT)\" tugmasini bosing");
            return;
        }

        sendMessage(chatId, "⏳ Ovoz qayta ishlanmoqda...\n\n⌛ Iltimos kuting...");

        try {
            System.out.println("📥 Voice message received, fileId: " + fileId);

            // Download the voice file properly
            String downloadedFilePath = downloadVoiceFile(fileId);

            if (downloadedFilePath != null && !downloadedFilePath.isEmpty()) {
                System.out.println("✅ File downloaded to: " + downloadedFilePath);

                // Convert voice to text using the service
                SttResponse response = botService.convertVoiceToText(downloadedFilePath);

                if (response != null && response.getText() != null && !response.getText().isEmpty()) {
                    System.out.println("✅ STT Response text: " + response.getText());
                    sendMessage(chatId, "✅ Natija:\n\n" + response.getText());
                } else {
                    System.err.println("❌ STT Response is empty or null");
                    sendMessage(chatId, "❌ Ovozni qayta ishlashda xato. Iltimos, qayta urinib ko'ring");
                }
            } else {
                System.err.println("❌ File download failed");
                sendMessage(chatId, "❌ Faylni yuklashda xato");
            }
        } catch (Exception e) {
            System.err.println("❌ Voice processing error: " + e.getMessage());
            e.printStackTrace();
            sendMessage(chatId, "❌ Xato: " + e.getMessage());
        }

        // Reset state
        userStates.put(chatId, "STARTED");
        sendStartMessage(chatId);
    }

    private void handleTextToSpeech(long chatId, String text) {
        if (text.length() < 2) {
            sendMessage(chatId, "❌ Matn juda qisqa. Kamida 2 ta belgi kiriting");
            return;
        }

        if (text.length() > 1000) {
            sendMessage(chatId, "❌ Matn juda uzun. Maksimal 1000 ta belgi");
            return;
        }

        sendMessage(chatId, "⏳ Audio yaratilmoqda...\n\n⌛ Iltimos kutib turun (bu 1-2 daqiqa vaqt olishi mumkin)");

        try {
            // Create TTS request
            TtsRequest ttsRequest = new TtsRequest();
            ttsRequest.setText(text);
            ttsRequest.setModel("lola"); // Default model
            ttsRequest.setBlocking(true);

            // Convert text to speech
            String audioUrl = botService.convertTextToSpeech(ttsRequest);

            if (audioUrl != null && !audioUrl.isEmpty()) {
                sendMessage(chatId, "✅ Audio tayyor! Yuborish boshlandi...");
                // Send audio to user
                sendAudio(chatId, audioUrl, text);
            } else {
                sendMessage(chatId, "❌ Audioyu yaratishda xato. Iltimos, qayta urinib ko'ring");
            }
        } catch (Exception e) {
            System.err.println("❌ TTS error: " + e.getMessage());
            String errorMsg = e.getMessage();
            if (errorMsg.contains("vaqti tugadi")) {
                sendMessage(chatId, "⏰ Audio yaratish vaqti tugadi.\n\nMatni qisqartib urinib ko'ring yoki /cancel bilan bekor qiling");
            } else {
                sendMessage(chatId, "❌ Xato: " + errorMsg);
            }
        }

        // Reset state
        userStates.put(chatId, "STARTED");
        sendStartMessage(chatId);
    }

    private void sendStartMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("👋 Assalomu aleykum! Men TTS/STT botiman.\n\n" +
                "📌 Menga matn yuborsangiz, unga audio qilib qaytaraman\n" +
                "🎤 Menga ovoz yuborsangiz, matnga o'tkazilib qaytaraman\n\n" +
                "Quyidagi tugmalarni tanlang:");

        ReplyKeyboardMarkup keyboardMarkup = getMainKeyboard();
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ SendMessage error: " + e.getMessage());
        }
    }

    private void sendHelpMessage(long chatId) {
        String helpText = "🆘 Yordam:\n\n" +
                "/start - Botni qayta boshlash\n" +
                "/help - Bu habar\n" +
                "/cancel - Joriy operatsiyani bekor qilish\n\n" +
                "📝 Matnga o'tkazish (STT):\n" +
                "1. \"📝 Matnga o'tkazish (STT)\" tugmasini bosing\n" +
                "2. Ovoz xabarini yuboring\n" +
                "3. Bot sizga matni qaytaradi\n\n" +
                "🎵 Nutqqa o'tkazish (TTS):\n" +
                "1. \"🎵 Nutqqa o'tkazish (TTS)\" tugmasini bosing\n" +
                "2. Matnni yuboring\n" +
                "3. Bot sizga audio qaytaradi (1-2 daqiqa kutib turish mumkin)\n\n" +
                "⚠️ Chegaralar:\n" +
                "- Matn: 2-1000 belgi\n" +
                "- Ovoz: Maksimal 50 MB\n" +
                "- Audio yaratish: Maksimal 2 minut";
        sendMessage(chatId, helpText);
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ SendMessage error: " + e.getMessage());
        }
    }

    private void sendAudio(long chatId, String audioUrl, String caption) {
        SendAudio audio = new SendAudio();
        audio.setChatId(chatId);
        audio.setAudio(new InputFile(audioUrl));
        String shortCaption = caption.length() > 100 ? caption.substring(0, 100) + "..." : caption;
        audio.setCaption("🎵 Matn: " + shortCaption);

        try {
            execute(audio);
        } catch (TelegramApiException e) {
            System.err.println("❌ SendAudio error: " + e.getMessage());
            sendMessage(chatId, "❌ Audio yuborish xatosi: " + e.getMessage());
        }
    }

    private String downloadVoiceFile(String fileId) {
        try {
            System.out.println("📥 Starting file download...");
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);

            File file = execute(getFileMethod);
            String filePath = file.getFilePath();
            System.out.println("📂 Telegram file path: " + filePath);

            String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
            System.out.println("🔗 Download URL: " + fileUrl);

            // Download file
            URL url = new URL(fileUrl);
            URLConnection connection = url.openConnection();
            InputStream inputStream = connection.getInputStream();

            // Save to temporary file with .ogg extension
            String tempPath = System.getProperty("java.io.tmpdir") + "/" + System.currentTimeMillis() + ".ogg";
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
            System.out.println("✅ Voice file downloaded successfully!");
            System.out.println("📁 Path: " + tempPath);
            System.out.println("📊 Size: " + totalBytes + " bytes");
            System.out.println("✔️ File exists: " + tempFile.exists());

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
