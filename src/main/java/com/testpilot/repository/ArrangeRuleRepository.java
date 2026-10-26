package com.testpilot.repository;

import com.testpilot.domain.ArrangeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 编排规则 Repository。
 * <p>
 * 规则库是共享可见的，所以列表查询不按 userId 过滤，
 * 编辑权限由 Service 层判断（spec/SPEC-2026-10-15.md §2）。
 */
public interface ArrangeRuleRepository extends JpaRepository<ArrangeRule, Long> {

    List<ArrangeRule> findByTaskId(Long taskId);

    List<ArrangeRule> findAllByOrderByIdDesc();

    @Transactional
    void deleteByTaskId(Long taskId);

    /** Keep task scenes in the shared rule library when their task is re-recorded. */
    @Modifying
    @Transactional
    @Query("update ArrangeRule r set r.taskId = null where r.taskId = :taskId")
    void detachFromTask(@Param("taskId") Long taskId);
}
