package com.example.blog.service;

import com.example.blog.dto.EventMessage;

/**
 * 业务事件投递（RabbitMQ）
 */
public interface EventPublisher {

    /**
     * 在当前事务提交后投递事件；没有事务时立即投递
     * <p>必须在事务提交后再投递：事务内直发的话消息可能先于提交到达消费者，
     * 消费者查库会查不到刚插入的记录。事务回滚则事件不发送。</p>
     *
     * @param routingKey 路由键，见 RabbitConfig 的 ROUTING_* 常量
     * @param message    事件消息
     */
    void publishAfterCommit(String routingKey, EventMessage message);

    /**
     * 立即投递（无事务场景使用）
     */
    void publish(String routingKey, EventMessage message);
}