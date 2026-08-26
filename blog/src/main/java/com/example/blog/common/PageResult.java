package com.example.blog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页返回结果
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> {

    /** 总条数 */
    private Long total;

    /** 当前页 */
    private Long pageNum;

    /** 每页大小 */
    private Long pageSize;

    /** 数据列表 */
    private List<T> list;

    /**
     * 由 MyBatis-Plus 分页对象转换
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T> PageResult<T> of(List<T> list, Long total, Long pageNum, Long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setList(list);
        return result;
    }
}
