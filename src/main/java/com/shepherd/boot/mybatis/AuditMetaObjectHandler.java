package com.shepherd.boot.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.shepherd.boot.audit.AuditorProvider;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final AuditorProvider auditorProvider;

    public AuditMetaObjectHandler(AuditorProvider auditorProvider) {
        this.auditorProvider = auditorProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
        auditorProvider.currentUserId().ifPresent(uid -> {
            strictInsertFill(metaObject, "createBy", Long.class, uid);
            strictInsertFill(metaObject, "updateBy", Long.class, uid);
        });
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        auditorProvider.currentUserId()
                .ifPresent(uid -> strictUpdateFill(metaObject, "updateBy", Long.class, uid));
    }
}
