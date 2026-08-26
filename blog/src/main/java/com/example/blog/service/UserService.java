package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.dto.LoginDTO;
import com.example.blog.dto.RegisterDTO;
import com.example.blog.entity.User;
import com.example.blog.vo.LoginVO;
import com.example.blog.vo.UserVO;

/**
 * 用户服务
 */
public interface UserService extends IService<User> {

    /**
     * 注册：校验用户名/邮箱唯一性，密码 BCrypt 加密后入库
     */
    UserVO register(RegisterDTO dto);

    /**
     * 登录：校验用户名密码，成功签发 JWT
     */
    LoginVO login(LoginDTO dto);
}
