package com.example.blog.dto;

/**
 * 所有经 RabbitMQ 投递的业务消息
 * <p>共同的唯一约束就是「必须带幂等键」：同一个事件重复投递时，消费者据此跳过。
 * 通知靠 {@code notification.uk_event_key} 永久去重，热度加分靠
 * {@code SETNX hot:dedup:{eventKey}}（TTL 2 小时）去重。</p>
 */
public interface EventMessage {

    /**
     * 业务事件唯一键，例如 {@code LIKE:12} / {@code COMMENT:34} / {@code HOT_FAV:56}
     */
    String getEventKey();
}