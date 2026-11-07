package kz.zhsr.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.zhsr.app.dto.user.ChangePasswordRequest;
import kz.zhsr.app.dto.user.CurrentState;
import kz.zhsr.app.dto.user.LoginRequest;
import kz.zhsr.app.dto.user.UserInfo;
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
    public void login(@RequestBody LoginRequest req,
                      HttpServletRequest request, HttpServletResponse response) throws Exception {
        sessionService.login(req, request, response);
    }

    @PostMapping("/session/password")
    public void changePassword(@RequestBody ChangePasswordRequest req) throws Exception {
        sessionService.changePassword(req);
    }

    @PostMapping("/session/logout")
    public void logout(HttpServletRequest request) {
        sessionService.logout(request);
    }

    @GetMapping("/getUserInfo")
    public UserInfo user() throws Exception {
        return sessionService.user();
    }

    @GetMapping("/session/state")
    public CurrentState state() {
        return sessionService.state();
    }

}
