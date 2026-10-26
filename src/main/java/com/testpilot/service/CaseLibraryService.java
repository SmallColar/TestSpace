package com.testpilot.service;

import com.testpilot.common.BusinessException;
import com.testpilot.common.Perms;
import com.testpilot.domain.TestCase;
import com.testpilot.dto.CaseRequest;
import com.testpilot.dto.CaseResponse;
import com.testpilot.enums.CaseStatus;
import com.testpilot.repository.TestCaseRepository;
import com.testpilot.repository.UserRepository;
import com.testpilot.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自动化用例库（spec/SPEC-2026-10-15.md §6）。
 * <p>
 * 共享可见：所有已认证用户都能看全部用例；只有自己的能改，管理员能改全部。
 */
@Service
@RequiredArgsConstructor
public class CaseLibraryService {

    private final TestCaseRepository testCaseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CaseResponse> list() {
        return testCaseRepository.findAllByOrderByIdDesc().stream()
                .map(tc -> withCreator(CaseResponse.from(tc, Perms.canEdit(tc.getUserId())), tc.getUserId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse detail(Long id) {
        TestCase tc = load(id);
        return withCreator(CaseResponse.from(tc, Perms.canEdit(tc.getUserId())), tc.getUserId());
    }

    @Transactional
    public CaseResponse create(CaseRequest req) {
        TestCase tc = new TestCase();
        tc.setUserId(UserContext.userId());
        tc.setSourceType("MANUAL_CREATED");
        tc.setName(req.getName() == null ? "未命名用例" : req.getName());
        tc.setDescription(req.getDescription());
        tc.setSteps(req.getSteps());
        tc.setAssertions(req.getAssertions());
        tc.setPublicVars(req.getPublicVars());
        tc.setStatus(CaseStatus.CONFIRMED);
        tc.setEnabled(req.getEnabled() == null || req.getEnabled());
        return withCreator(CaseResponse.from(testCaseRepository.save(tc), true), tc.getUserId());
    }

    @Transactional
    public CaseResponse copy(Long sourceId, CaseRequest request) {
        TestCase source = load(sourceId);
        if (request.getName() == null || request.getName().isBlank()) throw new BusinessException(40001, "用例名称不能为空");
        TestCase copy = new TestCase();
        copy.setUserId(UserContext.userId());
        copy.setTaskId(source.getTaskId());
        copy.setRuleId(source.getRuleId());
        copy.setName(request.getName().trim());
        copy.setDescription(request.getDescription() == null ? source.getDescription() : request.getDescription());
        copy.setSteps(request.getSteps() == null ? source.getSteps() : request.getSteps());
        copy.setAssertions(request.getAssertions() == null ? source.getAssertions() : request.getAssertions());
        copy.setParams(source.getParams());
        copy.setPublicVars(request.getPublicVars() == null ? source.getPublicVars() : request.getPublicVars());
        copy.setSourceType(source.getSourceType());
        copy.setStatus(CaseStatus.CONFIRMED);
        copy.setEnabled(true);
        return withCreator(CaseResponse.from(testCaseRepository.save(copy), true), copy.getUserId());
    }

    @Transactional
    public CaseResponse update(CaseRequest req) {
        TestCase tc = load(req.getId());
        Perms.requireEdit(tc.getUserId(), "自动化用例");
        if (req.getName() != null) {
            tc.setName(req.getName());
        }
        if (req.getDescription() != null) {
            tc.setDescription(req.getDescription());
        }
        if (req.getSteps() != null) {
            tc.setSteps(req.getSteps());
        }
        if (req.getAssertions() != null) {
            tc.setAssertions(req.getAssertions());
        }
        if (req.getPublicVars() != null) {
            tc.setPublicVars(req.getPublicVars());
        }
        if (req.getEnabled() != null) {
            tc.setEnabled(req.getEnabled());
        }
        return withCreator(CaseResponse.from(testCaseRepository.save(tc), true), tc.getUserId());
    }

    @Transactional
    public void delete(Long id) {
        TestCase tc = load(id);
        Perms.requireEdit(tc.getUserId(), "自动化用例");
        testCaseRepository.delete(tc);
    }

    @Transactional(readOnly = true)
    public TestCase load(Long id) {
        return testCaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "自动化用例不存在：" + id));
    }

    private CaseResponse withCreator(CaseResponse response, Long userId) {
        if (userId != null) userRepository.findById(userId).ifPresent(user -> response.setCreatorName(
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname()));
        return response;
    }
}
