package com.shepherd.boot.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分页插件的数据库方言。原先硬编码为 POSTGRE_SQL，提为配置项后
 * 下游用 MySQL 等其它库时无需覆盖整个 MybatisPlusInterceptor Bean。
 * <p>
 * 依赖 Spring Boot 的宽松绑定：{@code postgre_sql} / {@code postgre-sql} /
 * {@code POSTGRE_SQL} 均可绑定到 {@link DbType#POSTGRE_SQL}。
 */
@Data
@ConfigurationProperties(prefix = "shepherd.mybatis")
public class ShepherdMybatisProperties {
    private DbType dbType = DbType.POSTGRE_SQL;
}
