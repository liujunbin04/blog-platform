package com.example.blog.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 投递确认与退回回调
 * <p>目的只有一个：让「消息没送到」变成可观测的。没有这两个回调时，
 * 投递失败只会在发送端留下一行日志，没人知道消息去了哪里。</p>
 * <p>刻意<b>不做</b>本地重试：没有持久化重试队列的「重投两次」在进程挂掉时一样会丢，
 * 只会制造「看起来很可靠」的假象。通知与热度都是最终一致，丢一条由 30 分钟重建
 * 或人工重放 DLQ 兜底即可。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitConfirmConfig {

    private final RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息未被 broker 确认（可能进死信或丢失）: id={} 原因={}",
                        correlationData == null ? null : correlationData.getId(), cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "消息路由不到队列，已退回: exchange={} routingKey={} replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}