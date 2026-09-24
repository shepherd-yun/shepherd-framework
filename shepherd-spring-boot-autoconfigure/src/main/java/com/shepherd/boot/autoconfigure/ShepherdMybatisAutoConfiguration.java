package com.shepherd.boot.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.shepherd.boot.audit.AuditorProvider;
import com.shepherd.boot.mybatis.AuditMetaObjectHandler;
import com.shepherd.boot.mybatis.ShepherdMybatisProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(ShepherdMybatisProperties.class)
public class ShepherdMybatisAutoConfiguration {

    /**
     * 下游声明的 {@link InnerInterceptor} Bean（数据权限、多租户、乐观锁等）会被收集进来，
     * 按 {@code @Order} 排序，分页插件由 Starter 固定追加在最后（下游声明了自己的分页插件时用下游的）。
     *
     * <p>分页必须在最后：分页插件自己拼 COUNT 语句，用的是它执行那一刻的 SQL。
     * 数据权限若排在它之后，COUNT 不带过滤条件，列表只显示本公司数据、总数却是全表的。
     * 这是下游最容易踩的顺序坑，所以不交给下游自己排，而是由 Starter 钉死。
     *
     * <p>这样下游不必为了加一个插件而整体替换本 Bean（替换后分页插件得自己加回来、顺序得自己记住）。
     * 仍可整体替换：声明自己的 {@link MybatisPlusInterceptor} 即接管，此时收集逻辑与分页插件都不再生效。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(ShepherdMybatisProperties properties,
                                                         ObjectProvider<InnerInterceptor> innerInterceptors) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        List<InnerInterceptor> declared = innerInterceptors.orderedStream().toList();
        declared.stream()
                .filter(i -> !(i instanceof PaginationInnerInterceptor))
                .forEach(interceptor::addInnerInterceptor);
        // 下游若自己声明了分页插件（比如要设 maxLimit），用下游的那个替换 Starter 默认的，但位置仍固定在最后
        PaginationInnerInterceptor pagination = declared.stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .reduce((a, b) -> {
                    throw new IllegalStateException("容器里声明了多个 PaginationInnerInterceptor Bean，只能保留一个");
                })
                .orElseGet(() -> new PaginationInnerInterceptor(properties.getDbType()));
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public AuditMetaObjectHandler auditMetaObjectHandler(ObjectProvider<AuditorProvider> auditorProvider) {
        // 非 Web 应用下 ShepherdWebAutoConfiguration 不生效，兜底 AuditorProvider 不存在，
        // 此时回落为「不填充操作者」，而不是让整个上下文启动失败。
        return new AuditMetaObjectHandler(
                auditorProvider.getIfAvailable(() -> Optional::empty));
    }
}
