package com.testpilot.repository;

import com.testpilot.domain.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 自动化用例 Repository。
 * <p>
 * 用例库共享可见，列表不按 userId 过滤，编辑权限在 Service 层判断。
 */
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByTaskId(Long taskId);

    List<TestCase> findAllByOrderByIdDesc();

    List<TestCase> findByUserIdOrderByIdDesc(Long userId);

    Optional<TestCase> findByUniqueKey(String uniqueKey);

    @Transactional
    void deleteByTaskId(Long taskId);
}
