package com.testpilot.controller;

import com.testpilot.common.R;
import com.testpilot.dto.CaseRequest;
import com.testpilot.dto.CaseResponse;
import com.testpilot.service.CaseLibraryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自动化用例库：查看共享，编辑仅限自己的，管理员可编辑全部。
 */
@RestController
@RequestMapping("/api/cases")
public class CaseLibraryController {

    private final CaseLibraryService caseLibraryService;

    public CaseLibraryController(CaseLibraryService caseLibraryService) {
        this.caseLibraryService = caseLibraryService;
    }

    @GetMapping
    public R<List<CaseResponse>> list() {
        return R.ok(caseLibraryService.list());
    }

    @GetMapping("/detail")
    public R<CaseResponse> detail(@RequestParam Long id) {
        return R.ok(caseLibraryService.detail(id));
    }

    @PostMapping
    public R<CaseResponse> create(@RequestBody CaseRequest req) {
        return R.ok(caseLibraryService.create(req));
    }

    public record CopyCaseRequest(Long sourceCaseId, CaseRequest caseData) {}

    @PostMapping("/copy")
    public R<CaseResponse> copy(@RequestBody CopyCaseRequest req) {
        return R.ok(caseLibraryService.copy(req.sourceCaseId(), req.caseData()));
    }

    @PutMapping("/update")
    public R<CaseResponse> update(@RequestBody CaseRequest req) {
        return R.ok(caseLibraryService.update(req));
    }

    @DeleteMapping("/delete")
    public R<Void> delete(@RequestParam Long id) {
        caseLibraryService.delete(id);
        return R.ok();
    }

}
