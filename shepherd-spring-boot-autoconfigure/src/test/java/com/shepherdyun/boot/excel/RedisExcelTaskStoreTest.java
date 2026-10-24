package com.shepherdyun.boot.excel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisExcelTaskStoreTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisExcelTaskStore store;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        ShepherdExcelProperties props = new ShepherdExcelProperties();
        props.getTask().setTtl(Duration.ofHours(6));
        store = new RedisExcelTaskStore(redis, props, jsonMapper);
    }

    @Test
    void save_writes_json_under_a_namespaced_key_with_ttl_in_one_call() {
        ExcelTask task = ExcelTask.pending("EX1", ExcelTaskType.EXPORT, "admin", "订单.xlsx");

        store.save(task);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        // 写入与设过期必须一次完成——分两次调用时中间崩溃会留下永不过期的 key
        verify(ops).set(key.capture(), value.capture(), ttl.capture());

        assertThat(key.getValue()).isEqualTo("shepherd:excel:task:EX1");
        assertThat(ttl.getValue()).isEqualTo(Duration.ofHours(6));
        assertThat(value.getValue()).contains("\"id\":\"EX1\"").contains("\"ownerId\":\"admin\"");
    }

    @Test
    void find_round_trips_every_field() {
        ExcelTask original = ExcelTask.pending("EX1", ExcelTaskType.EXPORT, "admin", "订单.xlsx")
                .withProgress(120, 5000)
                .succeeded("excel/export/EX1.xlsx");
        when(ops.get("shepherd:excel:task:EX1")).thenReturn(jsonMapper.writeValueAsString(original));

        ExcelTask found = store.find("EX1").orElseThrow();

        assertThat(found).isEqualTo(original);
    }

    @Test
    void find_returns_empty_when_the_key_has_expired() {
        when(ops.get(anyString())).thenReturn(null);

        assertThat(store.find("EX-gone")).isEmpty();
    }

    @Test
    void find_returns_empty_on_unreadable_payload_rather_than_blowing_up() {
        // 换过 ExcelTask 结构后，旧 TTL 内的残留 JSON 仍可能反序列化失败
        when(ops.get(anyString())).thenReturn("{\"unexpected\":true}");

        assertThat(store.find("EX1")).isEmpty();
    }
}
