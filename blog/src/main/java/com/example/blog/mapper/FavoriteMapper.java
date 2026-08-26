package com.example.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.blog.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏记录 Mapper
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
