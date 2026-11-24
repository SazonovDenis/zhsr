package kz.zhsr.app;

import kz.kis.kisdbtools.config.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.context.annotation.*;

/**
 * Сервер zhsr: REST API для zhsr-web. Фронт ходит на /api/....
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@Import(DbToolsConfig.class)
public class ZhsrApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhsrApplication.class, args);
    }
}
