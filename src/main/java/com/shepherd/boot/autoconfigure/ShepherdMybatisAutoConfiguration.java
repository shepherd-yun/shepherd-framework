package com.shepherd.boot.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.shepherd.boot.core.audit.AuditorProvider;
import com.shepherd.boot.mybatis.AuditMetaObjectHandler;
import com.shepherd.boot.mybatis.ShepherdMybatisProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(ShepherdMybatisProperties.class)
public class ShepherdMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(ShepherdMybatisProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(properties.getDbType()));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public AuditMetaObjectHandler auditMetaObjectHandler(ObjectProvider<AuditorProvider> auditorProvider) {
        // 非 Web 应用下 ShepherdCoreAutoConfiguration 不生效，兜底 AuditorProvider 不存在，
        // 此时回落为「不填充操作者」，而不是让整个上下文启动失败。
        return new AuditMetaObjectHandler(
                auditorProvider.getIfAvailable(() -> Optional::empty));
    }
}
