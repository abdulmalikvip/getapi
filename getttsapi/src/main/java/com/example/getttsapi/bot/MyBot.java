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

    private Map<Long, String> userStates = new HashMap<>();
    private Map<Long, String> userLanguages = new HashMap<>();

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
                sendMessage(chatId, "❌ Operatsiya bekor qilindi.");
                sendStartMessage(chatId);
            } else if (userStates.getOrDefault(chatId, "").equals("WAITING_FOR_TEXT")) {
                // Matnni TTS ga yuborish
                handleTextToSpeech(chatId, text);
            } else {
                sendMessage(chatId, "❓ Noto'g'ri buyruq. /start bilan boshlang.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in handleTextMessage: " + e.getMessage());
            sendMessage(chatId, "❌ Xato: " + e.getMessage());
        }
    }

    private void handleVoiceMessage(long chatId, String fileId) {
        try {
            String state = userStates.getOrDefault(chatId, "");

            if (!state.equals("WAITING_FOR_VOICE")) {
                sendMessage(chatId, "❌ Avval \"📝 Matnga o'tkazish (STT)\" tugmasini bosing");
                return;
            }

            sendMessage(chatId, "⏳ Ovoz qayta ishlanmoqda...\n\n⌛ Iltimos kuting...");

            System.out.println("📥 Voice message received, fileId: " + fileId);

            // Ovoz faylini yuklab olish
            String downloadedFilePath = downloadVoiceFile(fileId);

            if (downloadedFilePath != null && !downloadedFilePath.isEmpty()) {
                System.out.println("✅ File downloaded to: " + downloadedFilePath);

                // Ovozni matnga o'tkazish
                SttResponse response = botService.convertVoiceToText(downloadedFilePath);

                if (response != null && response.getText() != null && !response.getText().isEmpty()) {
                    System.out.println("✅ STT Response text: " + response.getText());
                    sendMessage(chatId, "✅ Natija:\n\n" + response.getText());
                } else {
                    System.err.println("❌ STT Response is empty or null");
                    sendMessage(chatId, "❌ Ovozni qayta ishlashda xato. Iltimos, qayta urinib ko'ring");
                }

                // Vaqtinchalik faylni o'chirish
                new File(downloadedFilePath).delete();
            } else {
                System.err.println("❌ File download failed");
                sendMessage(chatId, "❌ Faylni yuklashda xato");
            }

        } catch (Exception e) {
            System.err.println("❌ Voice processing error: " + e.getMessage());
            e.printStackTrace();
            sendMessage(chatId, "❌ Xato: " + e.getMessage());
        } finally {
            // Holat qayta tiklash
            userStates.put(chatId, "STARTED");
            try {
                sendStartMessage(chatId);
            } catch (Exception e) {
                System.err.println("Error sending start message: " + e.getMessage());
            }
        }
    }

    private void handleTextToSpeech(long chatId, String text) {
        try {
            if (text.length() < 2) {
                sendMessage(chatId, "❌ Matn juda qisqa. Kamida 2 ta belgi kiriting");
                return;
            }

            if (text.length() > 1000) {
                sendMessage(chatId, "❌ Matn juda uzun. Maksimal 1000 ta belgi");
                return;
            }

            sendMessage(chatId, "⏳ Audio yaratilmoqda...\n\n⌛ Iltimos kutib turun (bu 1-2 daqiqa vaqt olishi mumkin)");

            // TTS soʻrovi yaratish
            TtsRequest ttsRequest = new TtsRequest();
            ttsRequest.setText(text);
            ttsRequest.setModel("lola");
            ttsRequest.setBlocking(true);

            System.out.println("🔊 Converting text to speech: " + text);

            // Matni nutqqa aylantirish
            String audioUrl = botService.convertTextToSpeech(ttsRequest);

            if (audioUrl != null && !audioUrl.isEmpty() && !audioUrl.equals("PROCESSING")) {
                sendMessage(chatId, "✅ Audio tayyor! Yuborish boshlandi...");
                // Audioing foydalanuvchiga yuborish
                sendAudio(chatId, audioUrl, text);
            } else if (audioUrl != null && audioUrl.equals("PROCESSING")) {
                sendMessage(chatId, "⏳ Audio yaratilmoqda...\n\nBiroz vaqt o'tgach qayta tekshiring yoki /cancel bilan bekor qiling");
            } else {
                sendMessage(chatId, "❌ Audio yaratishda xato. Iltimos, qayta urinib ko'ring");
            }
        } catch (Exception e) {
            System.err.println("❌ TTS error: " + e.getMessage());
            e.printStackTrace();
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("vaqti tugadi")) {
                sendMessage(chatId, "⏰ Audio yaratish vaqti tugadi.\n\nMatni qisqartib urinib ko'ring");
            } else {
                sendMessage(chatId, "❌ Xato: " + errorMsg);
            }
        } finally {
            // Holat qayta tiklash
            userStates.put(chatId, "STARTED");
            try {
                sendStartMessage(chatId);
            } catch (Exception e) {
                System.err.println("Error sending start message: " + e.getMessage());
            }
        }
    }

    private void sendStartMessage(long chatId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("👋 Assalomu aleykum! Men TTS/STT botiman.\n\n" +
                    "📌 Menga matn yuborsangiz, unga audio qilib qaytaraman\n" +
                    "🎤 Menga ovoz yuborsangiz, matnga o'tkazilib qaytaraman\n\n" +
                    "Quyidagi tugmalarni tanlang:");

            ReplyKeyboardMarkup keyboardMarkup = getMainKeyboard();
            message.setReplyMarkup(keyboardMarkup);

            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ SendMessage error: " + e.getMessage());
        }
    }

    private void sendHelpMessage(long chatId) {
        try {
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
                    "3. Bot sizga audio qaytaradi\n\n" +
                    "⚠️ Chegaralar:\n" +
                    "- Matn: 2-1000 belgi\n" +
                    "- Ovoz: Maksimal 50 MB\n" +
                    "- Audio yaratish: Maksimal 2 minut";
            sendMessage(chatId, helpText);
        } catch (Exception e) {
            System.err.println("Error sending help message: " + e.getMessage());
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
            SendAudio audio = new SendAudio();
            audio.setChatId(chatId);
            audio.setAudio(new InputFile(audioUrl));
            String shortCaption = caption.length() > 100 ? caption.substring(0, 100) + "..." : caption;
            audio.setCaption("🎵 Matn: " + shortCaption);

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

            // Faylni yuklash
            URL url = new URL(fileUrl);
            URLConnection connection = url.openConnection();
            InputStream inputStream = connection.getInputStream();

            // Vaqtinchalik faylga saqlash (.ogg format)
            String tempPath = System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis() + ".ogg";
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
