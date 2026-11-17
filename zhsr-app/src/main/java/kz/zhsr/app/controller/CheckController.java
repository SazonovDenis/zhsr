package kz.zhsr.app.controller;

import kz.zhsr.app.dto.check.CheckDictBody;
import kz.zhsr.app.dto.check.CheckDictRow;
import kz.zhsr.app.dto.check.CheckDictValueBody;
import kz.zhsr.app.dto.check.CheckDictValueRow;
import kz.zhsr.app.dto.check.CheckForm;
import kz.zhsr.app.dto.check.CheckItemBody;
import kz.zhsr.app.dto.check.CheckItemRow;
import kz.zhsr.app.dto.check.CheckListDetail;
import kz.zhsr.app.dto.check.CheckListDraft;
import kz.zhsr.app.dto.check.CheckListFilter;
import kz.zhsr.app.dto.check.CheckListRow;
import kz.zhsr.app.dto.check.CheckNormBody;
import kz.zhsr.app.dto.check.CheckNormRow;
import kz.zhsr.app.dto.check.CheckTemplateBody;
import kz.zhsr.app.dto.check.CheckTemplateRow;
import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.service.check.CheckAdminService;
import kz.zhsr.app.service.check.CheckService;
import kz.zhsr.app.service.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * Чек-листы: только вызовы {@link CheckService} и {@link CheckAdminService}. Пути - как в zhsr-web (api/checks.ts).
 * Завершённый чек-лист - multipart: часть draft (JSON) и фото пунктов (части photo_&lt;id пункта шаблона&gt;).
 */
@RestController
public class CheckController {

    private final CheckService checkService;
    private final CheckAdminService checkAdminService;

    public CheckController(CheckService checkService, CheckAdminService checkAdminService) {
        this.checkService = checkService;
        this.checkAdminService = checkAdminService;
    }

    // --- проверки

    @GetMapping("/check-templates")
    public List<CheckTemplateRow> templates() throws Exception {
        return checkService.templates();
    }

    @GetMapping("/check-lists/form")
    public CheckForm form(@RequestParam long template, @RequestParam(required = false) Long wagon) throws Exception {
        return checkService.form(template, wagon);
    }

    @GetMapping("/check-lists")
    public List<CheckListRow> list(CheckListFilter filter, ListSort sort) throws Exception {
        return checkService.list(filter, sort);
    }

    @GetMapping("/check-lists/{id}")
    public CheckListDetail detail(@PathVariable long id) throws Exception {
        return checkService.detail(id);
    }

    @PostMapping("/check-lists")
    public CheckListDetail create(@RequestPart("draft") CheckListDraft draft,
                                  @RequestParam MultiValueMap<String, MultipartFile> files) throws Exception {
        return checkService.create(draft, files);
    }

    @PutMapping("/check-lists/{id}")
    public CheckListDetail update(@PathVariable long id, @RequestPart("draft") CheckListDraft draft,
                                  @RequestParam MultiValueMap<String, MultipartFile> files) throws Exception {
        return checkService.update(id, draft, files);
    }

    @GetMapping("/check-lists/photos/{id}")
    public ResponseEntity<InputStreamResource> photo(@PathVariable long id) throws Exception {
        FileStorage.StoredFile file = checkService.photo(id);
        return ResponseEntity.ok()
                .contentType(file.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.contentType()))
                // фото не меняется: новое фото - новый id
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(new InputStreamResource(file.content()));
    }

    // --- администрирование: шаблоны ответов

    @GetMapping("/admin/check-dicts")
    public List<CheckDictRow> dicts() throws Exception {
        return checkAdminService.dicts();
    }

    @PostMapping("/admin/check-dicts")
    public CheckDictRow createDict(@RequestBody CheckDictBody body) throws Exception {
        return checkAdminService.createDict(body);
    }

    @PutMapping("/admin/check-dicts/{id}")
    public CheckDictRow updateDict(@PathVariable long id, @RequestBody CheckDictBody body) throws Exception {
        return checkAdminService.updateDict(id, body);
    }

    @DeleteMapping("/admin/check-dicts/{id}")
    public void deleteDict(@PathVariable long id) throws Exception {
        checkAdminService.deleteDict(id);
    }

    @GetMapping("/admin/check-dicts/{id}/values")
    public List<CheckDictValueRow> dictValues(@PathVariable long id) throws Exception {
        return checkAdminService.dictValues(id);
    }

    @PostMapping("/admin/check-dicts/{id}/values")
    public CheckDictValueRow createDictValue(@PathVariable long id, @RequestBody CheckDictValueBody body)
            throws Exception {
        return checkAdminService.createDictValue(id, body);
    }

    @PutMapping("/admin/check-dict-values/{id}")
    public CheckDictValueRow updateDictValue(@PathVariable long id, @RequestBody CheckDictValueBody body)
            throws Exception {
        return checkAdminService.updateDictValue(id, body);
    }

    @DeleteMapping("/admin/check-dict-values/{id}")
    public void deleteDictValue(@PathVariable long id) throws Exception {
        checkAdminService.deleteDictValue(id);
    }

    // --- администрирование: шаблоны чек-листов, пункты, нормативы

    @GetMapping("/admin/check-templates")
    public List<CheckTemplateRow> adminTemplates() throws Exception {
        return checkAdminService.templates();
    }

    @PostMapping("/admin/check-templates")
    public CheckTemplateRow createTemplate(@RequestBody CheckTemplateBody body) throws Exception {
        return checkAdminService.createTemplate(body);
    }

    @PutMapping("/admin/check-templates/{id}")
    public CheckTemplateRow updateTemplate(@PathVariable long id, @RequestBody CheckTemplateBody body)
            throws Exception {
        return checkAdminService.updateTemplate(id, body);
    }

    @DeleteMapping("/admin/check-templates/{id}")
    public void deleteTemplate(@PathVariable long id) throws Exception {
        checkAdminService.deleteTemplate(id);
    }

    @GetMapping("/admin/check-templates/{id}/items")
    public List<CheckItemRow> items(@PathVariable long id) throws Exception {
        return checkAdminService.items(id);
    }

    @PostMapping("/admin/check-templates/{id}/items")
    public CheckItemRow createItem(@PathVariable long id, @RequestBody CheckItemBody body) throws Exception {
        return checkAdminService.createItem(id, body);
    }

    @PutMapping("/admin/check-items/{id}")
    public CheckItemRow updateItem(@PathVariable long id, @RequestBody CheckItemBody body) throws Exception {
        return checkAdminService.updateItem(id, body);
    }

    @DeleteMapping("/admin/check-items/{id}")
    public void deleteItem(@PathVariable long id) throws Exception {
        checkAdminService.deleteItem(id);
    }

    @GetMapping("/admin/check-items/{id}/norms")
    public List<CheckNormRow> norms(@PathVariable long id) throws Exception {
        return checkAdminService.norms(id);
    }

    @PostMapping("/admin/check-items/{id}/norms")
    public CheckNormRow createNorm(@PathVariable long id, @RequestBody CheckNormBody body) throws Exception {
        return checkAdminService.createNorm(id, body);
    }

    @PutMapping("/admin/check-norms/{id}")
    public CheckNormRow updateNorm(@PathVariable long id, @RequestBody CheckNormBody body) throws Exception {
        return checkAdminService.updateNorm(id, body);
    }

    @DeleteMapping("/admin/check-norms/{id}")
    public void deleteNorm(@PathVariable long id) throws Exception {
        checkAdminService.deleteNorm(id);
    }

}
