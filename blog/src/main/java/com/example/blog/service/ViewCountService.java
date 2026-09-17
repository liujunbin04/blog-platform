package com.example.blog.service;

/**
 * 文章浏览量计数（Redis 累加 + 定时回写 MySQL）
 * <p>
 * 背景：详情页原本每访问一次就同步执行一次
 * {@code UPDATE article SET view_count = view_count + 1}，
 * 于是这个「读」接口实际上按「写」接口的成本在跑——
 * 每次请求都要开事务、抢行锁、并触发一次 redo log 刷盘。
 * 压测实测这把吞吐死死锁在很低的水位，且热点文章的行锁竞争会让情况更糟。
 * </p>
 * <p>
 * 改造后：浏览量只在 Redis 里 INCR，详情页完全不写数据库；
 * 由定时任务每隔几分钟把绝对值批量刷回 MySQL。
 * </p>
 */
public interface ViewCountService {

    /**
     * 浏览量 +1 并返回最新值
     *
     * @param articleId  文章 id
     * @param dbBaseline 数据库里的当前值，仅当 Redis 中还没有计数时用作初始值
     *                   （首次访问，或 Redis 丢数据后重新累积）
     * @return 自增后的浏览量
     */
    long incrementAndGet(Long articleId, int dbBaseline);

    /**
     * 把 Redis 中累积的浏览量批量回写 MySQL
     *
     * @return 本次回写的文章数
     */
    int flushToDatabase();

    /**
     * 删除某篇文章的计数（文章被删除时调用，避免残留脏 key）
     */
    void remove(Long articleId);
}
