package com.testpilot.repository;

import com.testpilot.domain.AgentTask;
import com.testpilot.enums.TaskStage;
import com.testpilot.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 任务 Repository。
 */
public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {

    /** 普通用户：只看自己的任务 */
    List<AgentTask> findByUserIdOrderByIdDesc(Long userId);

    /** 管理员：看全部 */
    List<AgentTask> findAllByOrderByIdDesc();

    /** 带归属校验的精确查询 */
    Optional<AgentTask> findByIdAndUserId(Long id, Long userId);

    /** Running recording tasks left behind by a previous application process. */
    List<AgentTask> findByStatusAndStage(TaskStatus status, TaskStage stage);
}
