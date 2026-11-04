package kz.zhsr.app.controller;

import kz.zhsr.app.dto.DictData;
import kz.zhsr.app.dto.DictValue;
import kz.zhsr.app.service.dict.DictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Справочники для DictManager фронта (zhsr-web: api/dict.ts): только вызовы {@link DictService}.
 */
@RestController
@RequestMapping("/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/{name}")
    public DictData dict(@PathVariable String name) throws Exception {
        return dictService.dict(name);
    }

    @GetMapping("/{name}/{id}")
    public DictValue value(@PathVariable String name, @PathVariable long id) throws Exception {
        return dictService.value(name, id);
    }

    @PostMapping("/versions")
    public Map<String, String> versions(@RequestBody List<String> names) throws Exception {
        return dictService.versions(names);
    }

}
