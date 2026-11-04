package kz.zhsr.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Сверяет сотрудника из сеанса с {@link CurrentUserService#load} (кэш, затем БД): отключённый или удалённый
 * сотрудник теряет сеанс, изменённые роли и депо попадают в сеанс сразу.
 * <p>
 * Сотрудник, который должен сменить пароль ({@link CurrentUser#mustChangePassword}), получает на любой запрос, кроме входа,
 * смены пароля и выхода, ответ 406 {"message": "Смените пароль"}: фронт по нему переходит на страницу смены пароля.
 * <p>
 * Стоит после SecurityContextHolderFilter: SecurityContext из сеанса уже в потоке.
 */
public class CurrentUserFilter extends OncePerRequestFilter {

    /**
     * Запросы, разрешённые до смены пароля.
     */
    static final Set<String> PASSWORD_CHANGE_PATHS = Set.of("/session/password", "/session/login", "/session/logout");

    private final CurrentUserService currentUserService;

    public CurrentUserFilter(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser fresh = null;
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            fresh = currentUserService.load(user.id());
            HttpSession session = request.getSession(false);
            if (fresh == null) {
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
            } else if (!fresh.equals(user)) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(CurrentUserService.authentication(fresh));
                SecurityContextHolder.setContext(context);
                if (session != null) {
                    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
                }
            }
        }
        if (fresh != null && fresh.mustChangePassword()
                && !PASSWORD_CHANGE_PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()))) {
            response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Смените пароль\"}");
            return;
        }
        chain.doFilter(request, response);
    }

}
