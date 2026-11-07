package kz.zhsr.app.controller;

import kz.zhsr.app.dto.admin.AdminOption;
import kz.zhsr.app.dto.admin.AdminPage;
import kz.zhsr.app.dto.admin.AdminQuery;
import kz.zhsr.app.dto.admin.AdminTable;
import kz.zhsr.app.dto.admin.AdminUserDraft;
import kz.zhsr.app.dto.admin.AdminUserRow;
import kz.zhsr.app.service.admin.AdminService;
import kz.zhsr.app.service.admin.AdminUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Администрирование: общий редактор таблиц ({@link AdminService}) и редактор сотрудников ({@link AdminUserService}).
 * Только вызовы сервисов.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminUserService adminUserService;

    public AdminController(AdminService adminService, AdminUserService adminUserService) {
        this.adminService = adminService;
        this.adminUserService = adminUserService;
    }

    // --- общий редактор

    @GetMapping("/tables")
    public List<AdminTable> tables() {
        return adminService.tables();
    }

    @GetMapping("/tables/{table}/rows")
    public AdminPage<Map<String, Object>> rows(@PathVariable String table, AdminQuery query) throws Exception {
        return adminService.page(table, query);
    }

    @GetMapping("/tables/{table}/rows/{id}")
    public Map<String, Object> row(@PathVariable String table, @PathVariable long id) throws Exception {
        return adminService.get(table, id);
    }

    @PostMapping("/tables/{table}/rows")
    public Map<String, Object> create(@PathVariable String table, @RequestBody Map<String, Object> body) throws Exception {
        return adminService.create(table, body);
    }

    @PutMapping("/tables/{table}/rows/{id}")
    public Map<String, Object> update(@PathVariable String table, @PathVariable long id,
                                      @RequestBody Map<String, Object> body) throws Exception {
        return adminService.update(table, id, body);
    }

    @DeleteMapping("/tables/{table}/rows/{id}")
    public void delete(@PathVariable String table, @PathVariable long id) throws Exception {
        adminService.delete(table, id);
    }

    @GetMapping("/options/{table}")
    public List<AdminOption> options(@PathVariable String table) throws Exception {
        return adminService.options(table);
    }

    // --- сотрудники

    @GetMapping("/users")
    public AdminPage<AdminUserRow> users(AdminQuery query) throws Exception {
        return adminUserService.page(query);
    }

    @GetMapping("/users/{id}")
    public AdminUserRow user(@PathVariable long id) throws Exception {
        return adminUserService.get(id);
    }

    @PostMapping("/users")
    public AdminUserRow createUser(@RequestBody AdminUserDraft draft) throws Exception {
        return adminUserService.create(draft);
    }

    @PutMapping("/users/{id}")
    public AdminUserRow updateUser(@PathVariable long id, @RequestBody AdminUserDraft draft) throws Exception {
        return adminUserService.update(id, draft);
    }

}
