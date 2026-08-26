package com.example.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 user 表）
 */
@Data
@TableName("user")
public class User {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt 密文，只写不读，接口永不返回） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 昵称 */
    private String nickname;

    /** 逻辑删除：0 未删除 1 已删除（与 application.yml 全局配置一致） */
    @TableLogic
    private Integer deleted;

    /** 创建时间（由数据库默认值 CURRENT_TIMESTAMP 维护，插入时省略该列） */
    private LocalDateTime createTime;

    /** 更新时间（由数据库 ON UPDATE CURRENT_TIMESTAMP 维护） */
    private LocalDateTime updateTime;
}
