package kz.zhsr.app.controller;

import kz.zhsr.app.dto.ListSort;
import kz.zhsr.app.dto.RequestActionBody;
import kz.zhsr.app.dto.RequestCounts;
import kz.zhsr.app.dto.RequestDetail;
import kz.zhsr.app.dto.RequestDraft;
import kz.zhsr.app.dto.RequestExecutorBody;
import kz.zhsr.app.dto.RequestFilter;
import kz.zhsr.app.dto.RequestRow;
import kz.zhsr.app.dto.UserRef;
import kz.zhsr.app.service.request.RequestAction;
import kz.zhsr.app.service.request.RequestService;
import kz.zhsr.app.service.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Заявки на ремонт: только вызовы {@link RequestService}. Пути - как в zhsr-web (api/requests.ts). Подача,
 * исправление и пояснение - multipart: черновик (часть draft, JSON) или текст (text) и фото (части photos).
 */
@RestController
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/requests")
    public List<RequestRow> list(RequestFilter filter, ListSort sort) throws Exception {
        return requestService.list(filter, sort);
    }

    @GetMapping("/requests/counts")
    public RequestCounts counts(RequestFilter filter) throws Exception {
        return requestService.counts(filter);
    }

    @GetMapping("/requests/{id}")
    public RequestDetail detail(@PathVariable long id) throws Exception {
        return requestService.detail(id);
    }

    @PostMapping("/requests")
    public RequestRow create(@RequestPart("draft") RequestDraft draft,
                             @RequestPart(value = "photos", required = false) List<MultipartFile> photos)
            throws Exception {
        return requestService.create(draft, photos);
    }

    @PutMapping("/requests/{id}")
    public RequestRow update(@PathVariable long id, @RequestPart("draft") RequestDraft draft,
                             @RequestPart(value = "photos", required = false) List<MultipartFile> photos)
            throws Exception {
        return requestService.update(id, draft, photos);
    }

    @PostMapping("/requests/{id}/actions/{action}")
    public RequestDetail action(@PathVariable long id, @PathVariable RequestAction action,
                                @RequestBody(required = false) RequestActionBody body) throws Exception {
        return requestService.action(id, action, body == null ? null : body.comment());
    }

    @PutMapping("/requests/{id}/executor")
    public RequestDetail assign(@PathVariable long id, @RequestBody RequestExecutorBody body) throws Exception {
        return requestService.assign(id, body.executor());
    }

    @PostMapping("/requests/{id}/notes")
    public RequestDetail addNote(@PathVariable long id, @RequestParam(value = "text", required = false) String text,
                                 @RequestPart(value = "photos", required = false) List<MultipartFile> photos)
            throws Exception {
        return requestService.addNote(id, text, photos);
    }

    @GetMapping("/requests/photos/{id}")
    public ResponseEntity<InputStreamResource> photo(@PathVariable long id) throws Exception {
        FileStorage.StoredFile file = requestService.photo(id);
        return ResponseEntity.ok()
                .contentType(file.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.contentType()))
                // фото не меняется: новое фото - новый id
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(new InputStreamResource(file.content()));
    }

    @GetMapping("/services/{service}/staff")
    public List<UserRef> serviceStaff(@PathVariable long service) throws Exception {
        return requestService.serviceStaff(service);
    }

}
