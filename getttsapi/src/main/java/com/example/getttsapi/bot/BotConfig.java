package com.example.getttsapi.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfig {

    @Autowired
    private MyBot myBot;

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            botsApi.registerBot(myBot);
            System.out.println("✅ Bot registered successfully!");
        } catch (TelegramApiException e) {
            System.err.println("❌ Failed to register bot: " + e.getMessage());
            throw e;
        }
        return botsApi;
    }
}
