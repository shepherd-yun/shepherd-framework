package com.shepherd.boot.serialno;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shepherd.serial-no.* 配置。
 */
@Data
@ConfigurationProperties(prefix = "shepherd.serial-no")
public class ShepherdSerialNoProperties {

    /** 关闭则不装配 SerialNoGenerator。 */
    private boolean enabled = true;

    /** 计数器 key 前缀；多应用共享同一 Redis 时改它做隔离。 */
    private String keyPrefix = "shepherd:serialno:";
}
