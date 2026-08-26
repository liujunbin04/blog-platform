package com.example.blog.vo;

import lombok.Data;

/**
 * 登录返回对象
 */
@Data
public class LoginVO {

    /** JWT token，后续请求放在 Authorization: Bearer <token> 中 */
    private String token;

    /** 用户信息 */
    private UserVO user;
}
