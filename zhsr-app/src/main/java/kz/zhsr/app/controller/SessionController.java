package kz.zhsr.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.zhsr.app.dto.ChangePasswordRequest;
import kz.zhsr.app.dto.CurrentState;
import kz.zhsr.app.dto.LoginRequest;
import kz.zhsr.app.dto.UserInfo;
import kz.zhsr.app.service.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Сеанс сотрудника: только вызовы {@link SessionService}. Пути - как в zhsr-web (api/session.ts, api/state.ts).
 */
@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/session/login")
    public UserInfo login(@RequestBody LoginRequest req,
                                         HttpServletRequest request, HttpServletResponse response) throws Exception {
        return sessionService.login(req, request, response);
    }

    @PostMapping("/session/password")
    public UserInfo changePassword(@RequestBody ChangePasswordRequest req) throws Exception {
        return sessionService.changePassword(req);
    }

    @PostMapping("/session/logout")
    public void logout(HttpServletRequest request) {
        sessionService.logout(request);
    }

    @GetMapping("/getUserInfo")
    public UserInfo user() {
        return sessionService.user();
    }

    @GetMapping("/session/state")
    public CurrentState state() {
        return sessionService.state();
    }

}
