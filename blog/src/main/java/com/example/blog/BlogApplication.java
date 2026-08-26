package com.example.blog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博客平台后端服务启动类
 */
@SpringBootApplication
@MapperScan("com.example.blog.mapper")
@EnableScheduling // 启用定时任务（@Scheduled，见 task 包）

public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
