package com.shepherd.boot.web.advice;

import java.lang.annotation.*;

/**
 * 标在 controller 类或处理方法上，令 {@link ResultBodyAdvice} 放行其返回值不做包装。
 * 用于需要返回原始结构的接口（对接第三方回调、导出裸 JSON 等）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoResultWrap {
}
