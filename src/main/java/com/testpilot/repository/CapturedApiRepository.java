package com.testpilot.repository;

import com.testpilot.domain.CapturedApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 捕获接口 Repository。
 */
public interface CapturedApiRepository extends JpaRepository<CapturedApi, Long> {

    List<CapturedApi> findByTaskIdOrderBySeqAsc(Long taskId);

    /** 派生删除必须在事务里执行；流水线是异步线程，所以在这里显式声明 */
    @Transactional
    void deleteByTaskId(Long taskId);
}
