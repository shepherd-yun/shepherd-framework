package com.shepherdyun.boot.excel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * 任务态存 Redis：导出文件本就是临时产物，TTL 语义吻合，也不必往下游数据库建表。
 *
 * <p>代价是任务记录不持久——Redis 丢数时重导一次即可。要持久化就覆盖 {@link ExcelTaskStore} Bean。
 */
public class RedisExcelTaskStore implements ExcelTaskStore {

    private static final Logger log = LoggerFactory.getLogger(RedisExcelTaskStore.class);
    private static final String KEY_PREFIX = "shepherd:excel:task:";

    private final StringRedisTemplate redis;
    private final ShepherdExcelProperties properties;
    private final JsonMapper jsonMapper;

    public RedisExcelTaskStore(StringRedisTemplate redis, ShepherdExcelProperties properties,
                               JsonMapper jsonMapper) {
        this.redis = redis;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void save(ExcelTask task) {
        // set(key, value, ttl) 一次完成写入与设过期，不会留下永不过期的 key
        redis.opsForValue().set(KEY_PREFIX + task.id(),
                jsonMapper.writeValueAsString(task),
                properties.getTask().getTtl());
    }

    @Override
    public Optional<ExcelTask> find(String id) {
        String json = redis.opsForValue().get(KEY_PREFIX + id);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, ExcelTask.class));
        } catch (Exception ex) {
            // 结构变更后 TTL 内的残留 JSON 可能读不动——当作任务不存在，不要让查询接口 500
            log.warn("Excel 任务 {} 的记录无法反序列化，按不存在处理", id, ex);
            return Optional.empty();
        }
    }
}
