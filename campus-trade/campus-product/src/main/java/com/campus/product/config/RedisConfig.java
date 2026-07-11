package com.campus.product.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：统一 RedisTemplate 的序列化方式。
 * key/hashKey 用 String，value/hashValue 用 Jackson JSON。
 * 关键点：ObjectMapper 注册 JavaTimeModule，否则 ProductDetailVO 的 LocalDateTime createTime
 * 序列化会抛 InvalidDefinitionException（Java8 时间类型默认不支持）。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(buildObjectMapper());

        // key 用 String
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value 用 Jackson JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构建支持 LocalDateTime 的 ObjectMapper。
     * - JavaTimeModule：支持 java.time.* 序列化/反序列化。
     * - 关闭 WRITE_DATES_AS_TIMESTAMPS：以 ISO-8601 字符串而非数组形式写时间，更易读。
     * - activateDefaultTyping：GenericJackson2JsonRedisSerializer 需写入 @class 类型信息，
     *   才能把缓存反序列化回 ProductDetailVO 而非 LinkedHashMap。
     */
    static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        BasicPolymorphicTypeValidator allowedTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.campus.product.dto.")
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.math.BigDecimal")
                .build();
        mapper.activateDefaultTyping(
                allowedTypes,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfoAsProperty());
        return mapper;
    }

    private static com.fasterxml.jackson.annotation.JsonTypeInfo.As JsonTypeInfoAsProperty() {
        return com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
    }
}
