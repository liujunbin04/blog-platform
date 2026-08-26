package com.example.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.blog.common.BusinessException;
import com.example.blog.dto.LoginDTO;
import com.example.blog.dto.RegisterDTO;
import com.example.blog.entity.User;
import com.example.blog.mapper.UserMapper;
import com.example.blog.service.UserService;
import com.example.blog.utils.JwtUtil;
import com.example.blog.vo.LoginVO;
import com.example.blog.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public UserVO register(RegisterDTO dto) {
        // 用户名唯一性校验（逻辑删除用户不参与校验，MP 自动拼接 deleted=0）
        if (lambdaQuery().eq(User::getUsername, dto.getUsername()).count() > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        // 邮箱为可选字段，仅填写时校验唯一性
        if (StringUtils.hasText(dto.getEmail())
                && lambdaQuery().eq(User::getEmail, dto.getEmail()).count() > 0) {
            throw new BusinessException(400, "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        // 昵称未填时默认使用用户名
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        save(user);

        return UserVO.from(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = lambdaQuery().eq(User::getUsername, dto.getUsername()).one();
        // 用户不存在与密码错误的提示语保持一致，避免暴露账号是否存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generateToken(user.getId(), user.getUsername()));
        vo.setUser(UserVO.from(user));
        return vo;
    }
}
