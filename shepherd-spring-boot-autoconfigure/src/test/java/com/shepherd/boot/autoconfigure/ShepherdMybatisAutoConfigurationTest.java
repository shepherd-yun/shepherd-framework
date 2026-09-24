package com.shepherd.boot.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

class ShepherdMybatisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShepherdMybatisAutoConfiguration.class));

    /** 模拟下游的数据权限插件：会改写 SQL，必须排在分页之前。 */
    static class DataScopeInterceptor implements InnerInterceptor {}

    @Configuration(proxyBeanMethods = false)
    static class DownstreamInterceptors {
        @Bean
        @Order(2)
        OptimisticLockerInnerInterceptor optimisticLocker() {
            return new OptimisticLockerInnerInterceptor();
        }

        @Bean
        @Order(1)
        DataScopeInterceptor dataScope() {
            return new DataScopeInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DownstreamPagination {
        @Bean
        PaginationInnerInterceptor customPagination() {
            PaginationInnerInterceptor p = new PaginationInnerInterceptor(DbType.MYSQL);
            p.setMaxLimit(100L);
            return p;
        }

        @Bean
        DataScopeInterceptor dataScope() {
            return new DataScopeInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DownstreamWholeInterceptor {
        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            return new MybatisPlusInterceptor();
        }

        @Bean
        DataScopeInterceptor dataScope() {
            return new DataScopeInterceptor();
        }
    }

    @Test
    void without_downstream_interceptors_only_pagination_is_registered() {
        runner.run(ctx -> assertThat(ctx.getBean(MybatisPlusInterceptor.class).getInterceptors())
                .singleElement()
                .isInstanceOf(PaginationInnerInterceptor.class));
    }

    @Test
    void downstream_inner_interceptors_are_ordered_and_pagination_stays_last() {
        runner.withUserConfiguration(DownstreamInterceptors.class).run(ctx ->
                assertThat(ctx.getBean(MybatisPlusInterceptor.class).getInterceptors())
                        .extracting(Object::getClass)
                        .containsExactly(DataScopeInterceptor.class, OptimisticLockerInnerInterceptor.class,
                                PaginationInnerInterceptor.class));
    }

    /** 下游自带的分页插件替换 Starter 默认的（保住它的 maxLimit 等配置），但不能跑到数据权限前面去。 */
    @Test
    void downstream_pagination_replaces_default_and_is_still_placed_last() {
        runner.withUserConfiguration(DownstreamPagination.class).run(ctx -> {
            var interceptors = ctx.getBean(MybatisPlusInterceptor.class).getInterceptors();
            assertThat(interceptors).extracting(Object::getClass)
                    .containsExactly(DataScopeInterceptor.class, PaginationInnerInterceptor.class);
            assertThat(interceptors.get(1)).isSameAs(ctx.getBean("customPagination"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoPaginations {
        @Bean
        PaginationInnerInterceptor paginationA() {
            return new PaginationInnerInterceptor(DbType.MYSQL);
        }

        @Bean
        PaginationInnerInterceptor paginationB() {
            return new PaginationInnerInterceptor(DbType.MYSQL);
        }
    }

    /** 两个分页插件没法判断下游要哪个，任选一个都可能静默用错配置，所以启动失败。 */
    @Test
    void two_downstream_paginations_fail_fast() {
        runner.withUserConfiguration(TwoPaginations.class).run(ctx ->
                assertThat(ctx.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("PaginationInnerInterceptor"));
    }

    @Test
    void downstream_mybatis_plus_interceptor_takes_over_entirely() {
        runner.withUserConfiguration(DownstreamWholeInterceptor.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(MybatisPlusInterceptor.class);
            assertThat(ctx.getBean(MybatisPlusInterceptor.class).getInterceptors()).isEmpty();
        });
    }
}
