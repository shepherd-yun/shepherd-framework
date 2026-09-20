package com.shepherd.boot.mybatis;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMetaObjectHandlerTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class Sample extends BaseEntity {
        private Long id;
    }

    /**
     * strictInsertFill/strictUpdateFill 依赖 MyBatis-Plus 的 TableInfo 缓存，
     * 该缓存平时由 Spring 容器解析 Mapper 时填充；这里没有容器，手动触发一次初始化，
     * 使 Sample 的字段填充注解（继承自 BaseEntity）被正确识别。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "AuditMetaObjectHandlerTest");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
    }

    @Test
    void insert_fill_sets_timestamps_and_logical_delete_flag() {
        AuditMetaObjectHandler handler = new AuditMetaObjectHandler(Optional::empty);
        Sample entity = new Sample();
        MetaObject mo = SystemMetaObject.forObject(entity);

        handler.insertFill(mo);

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
        assertThat(entity.getDeleted()).isZero();
        assertThat(entity.getCreateBy()).isNull();
    }

    @Test
    void insert_fill_stamps_current_user_when_auditor_present() {
        AuditMetaObjectHandler handler = new AuditMetaObjectHandler(() -> Optional.of(42L));
        Sample entity = new Sample();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateBy()).isEqualTo(42L);
        assertThat(entity.getUpdateBy()).isEqualTo(42L);
    }

    @Test
    void update_fill_touches_update_fields_only() {
        AuditMetaObjectHandler handler = new AuditMetaObjectHandler(() -> Optional.of(7L));
        Sample entity = new Sample();

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateTime()).isNotNull();
        assertThat(entity.getUpdateBy()).isEqualTo(7L);
        assertThat(entity.getCreateTime()).isNull();
        assertThat(entity.getCreateBy()).isNull();
    }
}
