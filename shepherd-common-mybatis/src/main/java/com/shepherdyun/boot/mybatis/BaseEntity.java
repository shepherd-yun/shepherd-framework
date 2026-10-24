package com.shepherdyun.boot.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

@Data
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private @Nullable LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private @Nullable LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private @Nullable Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private @Nullable Long updateBy;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private @Nullable Integer deleted;
}
