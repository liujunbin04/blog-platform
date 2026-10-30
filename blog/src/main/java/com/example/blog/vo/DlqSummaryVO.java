package com.example.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 死信队列概览项
 */
@Data
@AllArgsConstructor
public class DlqSummaryVO {

    /** 队列名 */
    private String queue;

    /** 积压条数 */
    private long messageCount;
}