package com.example.blog.vo;

import com.example.blog.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息返回对象（不含密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String avatar;

    private LocalDateTime createTime;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
