package com.example.blog.vo;

import com.example.blog.entity.User;
import lombok.Data;

/**
 * 文章作者信息（昵称 + 头像）
 */
@Data
public class AuthorVO {

    private Long id;

    private String nickname;

    private String avatar;

    public static AuthorVO from(User user) {
        AuthorVO vo = new AuthorVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
