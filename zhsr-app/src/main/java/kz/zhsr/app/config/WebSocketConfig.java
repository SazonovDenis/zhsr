package kz.zhsr.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket (STOMP) для zhsr-web (src/shared/ws.ts): точка подключения /ws, каналы - /topic/<имя>. Брокер - в памяти
 * приложения: экземпляр сервера один.
 * <p>
 * Подключение - только после входа (SecurityConfig: /ws не открыт), сеанс - по cookie. Любой Origin допустим: cookie
 * сеанса SameSite=Lax, с чужого сайта она при подключении не придёт.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

}
