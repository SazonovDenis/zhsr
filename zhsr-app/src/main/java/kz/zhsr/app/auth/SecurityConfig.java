package kz.zhsr.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

/**
 * Вход - своим запросом POST /session/login (SessionController): логин и пароль из таблицы usr, дальше - сеанс в
 * cookie. Встроенная форма входа Spring, Basic и logout Spring выключены. На весь API, кроме входа и сведений о
 * сборке, нужен вход; без входа - 401, а не перенаправление: страницу входа показывает фронт.
 * <p>
 * Права - по ролям сотрудника (authority ROLE_<role_type.code>), отдельные методы - через @PreAuthorize.
 * <p>
 * CSRF-токены не используются: cookie сеанса SameSite=Lax (application.properties), POST с чужого сайта её не
 * несёт.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CurrentUserService currentUserService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .securityContext(c -> c.securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/session/login", "/session/logout", "/buildInfo", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(SecurityConfig::loginRequired))
                .addFilterAfter(new CurrentUserFilter(currentUserService), SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * Нет входа: 401 {"message": "Требуется вход"} - фронт по нему переходит на страницу входа.
     */
    private static void loginRequired(HttpServletRequest request, HttpServletResponse response,
                                      AuthenticationException e) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Требуется вход\"}");
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
