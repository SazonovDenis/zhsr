package kz.zhsr.app.controller;

import kz.zhsr.app.dto.common.ListSort;
import kz.zhsr.app.dto.equip.EquipForm;
import kz.zhsr.app.dto.equip.EquipItemBody;
import kz.zhsr.app.dto.equip.EquipItemRow;
import kz.zhsr.app.dto.equip.EquipListDetail;
import kz.zhsr.app.dto.equip.EquipListDraft;
import kz.zhsr.app.dto.equip.EquipListFilter;
import kz.zhsr.app.dto.equip.EquipListRow;
import kz.zhsr.app.dto.equip.EquipNormBody;
import kz.zhsr.app.dto.equip.EquipNormRow;
import kz.zhsr.app.dto.equip.EquipTemplateBody;
import kz.zhsr.app.dto.equip.EquipTemplateRow;
import kz.zhsr.app.service.equip.EquipAdminService;
import kz.zhsr.app.service.equip.EquipService;
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
 * Описи экипировки: только вызовы {@link EquipService} и {@link EquipAdminService}. Пути - как в zhsr-web
 * (api/equip.ts). Завершённая опись - multipart: часть draft (JSON) и фото позиций (части photo_&lt;id пункта
 * шаблона&gt;).
 */
@RestController
public class EquipController {

    private final EquipService equipService;
    private final EquipAdminService equipAdminService;

    public EquipController(EquipService equipService, EquipAdminService equipAdminService) {
        this.equipService = equipService;
        this.equipAdminService = equipAdminService;
    }

    // --- описи

    @GetMapping("/equip-templates")
    public List<EquipTemplateRow> templates() throws Exception {
        return equipService.templates();
    }

    @GetMapping("/equip-lists/form")
    public EquipForm form(@RequestParam long template, @RequestParam long wagon,
                          @RequestParam(required = false) Long exclude) throws Exception {
        return equipService.form(template, wagon, exclude);
    }

    @GetMapping("/equip-lists")
    public List<EquipListRow> list(EquipListFilter filter, ListSort sort) throws Exception {
        return equipService.list(filter, sort);
    }

    /** Последняя опись вагона без ограничения видимости; описей нет - пустой ответ. */
    @GetMapping("/equip-lists/last")
    public EquipListDetail last(@RequestParam long wagon) throws Exception {
        return equipService.lastOfWagon(wagon);
    }

    @GetMapping("/equip-lists/{id}")
    public EquipListDetail detail(@PathVariable long id) throws Exception {
        return equipService.detail(id);
    }

    @PostMapping("/equip-lists")
    public EquipListDetail create(@RequestPart("draft") EquipListDraft draft,
                                  @RequestParam MultiValueMap<String, MultipartFile> files) throws Exception {
        return equipService.create(draft, files);
    }

    @PutMapping("/equip-lists/{id}")
    public EquipListDetail update(@PathVariable long id, @RequestPart("draft") EquipListDraft draft,
                                  @RequestParam MultiValueMap<String, MultipartFile> files) throws Exception {
        return equipService.update(id, draft, files);
    }

    @GetMapping("/equip-lists/photos/{id}")
    public ResponseEntity<InputStreamResource> photo(@PathVariable long id) throws Exception {
        FileStorage.StoredFile file = equipService.photo(id);
        return ResponseEntity.ok()
                .contentType(file.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.contentType()))
                // фото не меняется: новое фото - новый id
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(new InputStreamResource(file.content()));
    }

    // --- администрирование

    @GetMapping("/admin/equip-templates")
    public List<EquipTemplateRow> adminTemplates() throws Exception {
        return equipAdminService.templates();
    }

    @PostMapping("/admin/equip-templates")
    public EquipTemplateRow createTemplate(@RequestBody EquipTemplateBody body) throws Exception {
        return equipAdminService.createTemplate(body);
    }

    @PutMapping("/admin/equip-templates/{id}")
    public EquipTemplateRow updateTemplate(@PathVariable long id, @RequestBody EquipTemplateBody body)
            throws Exception {
        return equipAdminService.updateTemplate(id, body);
    }

    @DeleteMapping("/admin/equip-templates/{id}")
    public void deleteTemplate(@PathVariable long id) throws Exception {
        equipAdminService.deleteTemplate(id);
    }

    @GetMapping("/admin/equip-templates/{id}/items")
    public List<EquipItemRow> items(@PathVariable long id) throws Exception {
        return equipAdminService.items(id);
    }

    @PostMapping("/admin/equip-templates/{id}/items")
    public EquipItemRow createItem(@PathVariable long id, @RequestBody EquipItemBody body) throws Exception {
        return equipAdminService.createItem(id, body);
    }

    @PutMapping("/admin/equip-items/{id}")
    public EquipItemRow updateItem(@PathVariable long id, @RequestBody EquipItemBody body) throws Exception {
        return equipAdminService.updateItem(id, body);
    }

    @DeleteMapping("/admin/equip-items/{id}")
    public void deleteItem(@PathVariable long id) throws Exception {
        equipAdminService.deleteItem(id);
    }

    @GetMapping("/admin/equip-items/{id}/norms")
    public List<EquipNormRow> norms(@PathVariable long id) throws Exception {
        return equipAdminService.norms(id);
    }

    @PostMapping("/admin/equip-items/{id}/norms")
    public EquipNormRow createNorm(@PathVariable long id, @RequestBody EquipNormBody body) throws Exception {
        return equipAdminService.createNorm(id, body);
    }

    @PutMapping("/admin/equip-norms/{id}")
    public EquipNormRow updateNorm(@PathVariable long id, @RequestBody EquipNormBody body) throws Exception {
        return equipAdminService.updateNorm(id, body);
    }

    @DeleteMapping("/admin/equip-norms/{id}")
    public void deleteNorm(@PathVariable long id) throws Exception {
        equipAdminService.deleteNorm(id);
    }

}
