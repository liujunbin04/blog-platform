package com.example.blog.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring Cache 配置：使用 Redis 作为缓存管理器
 * <p>用法：Service 方法上标注 {@code @Cacheable(value = "缓存名", key = "#id")}、
 * {@code @CacheEvict}、{@code @CachePut}，注解生效依赖本类的 @EnableCaching</p>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Redis 缓存管理器
     * <ul>
     *   <li>默认过期 1 小时（单个缓存可用 withCacheConfiguration 覆盖）</li>
     *   <li>值用 Jackson JSON 序列化（Generic 版会写入类型信息，反序列化恢复原类型）</li>
     *   <li>key 用 StringRedisSerializer，统一加 blog:cache: 前缀</li>
     *   <li>不缓存 null，防止缓存穿透</li>
     * </ul>
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 值序列化器：显式注册 JavaTimeModule（LocalDateTime 等 java.time 类型
        // 在默认 ObjectMapper 下会序列化失败）+ 写入类型信息（@class），
        // 反序列化时才能恢复成原类型（如 ArticleVO、List<CommentVO>）
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 默认 typing 收紧为本项目包的白名单：Redis 若被写入伪造的 @class，
        // 反序列化会直接被拒绝，避免多态反序列化风险
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.blog")
                .build();
        mapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 默认过期时间 1 小时
                .entryTtl(Duration.ofMinutes(10))
                // key 前缀：缓存名 probe 的 key 形如 blog:cache:probe::1，避免与其他业务 key 冲突
                .prefixCacheNameWith("blog:cache:")
                // 不缓存 null：既省空间又防止"查不到的数据"反复击穿缓存
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                // 单个缓存可覆盖默认配置，例如热门文章 5 分钟过期：
                // .withCacheConfiguration("hotArticles",
                //         defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}
