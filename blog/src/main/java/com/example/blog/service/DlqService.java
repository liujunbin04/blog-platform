package com.example.blog.service;

import com.example.blog.vo.DlqSummaryVO;

import java.util.List;

/**
 * 死信队列管理：查看积压与重放
 */
public interface DlqService {

    /**
     * 列出全部死信队列的积压情况
     */
    List<DlqSummaryVO> summary();

    /**
     * 把指定死信队列头部的至多 count 条消息搬回原业务队列
     * <p>重放后消息从 DLQ 移除、回到业务队列；若再次消费失败会二次进 DLQ，
     * 这是预期行为，不做「重放计数器」。</p>
     *
     * @return 实际重放的条数
     */
    int replay(String queue, int count);
}