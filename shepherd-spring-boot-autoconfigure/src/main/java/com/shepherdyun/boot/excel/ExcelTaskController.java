package com.shepherdyun.boot.excel;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务状态查询端点。返回裸 VO，由 ResultBodyAdvice 自动包装成 Result。
 *
 * <p>这是 Starter 首个内置 controller——它是基础设施端点，不含业务模型，
 * 且可通过 shepherd.excel.task.endpoint-enabled=false 完全关闭由下游自行实现。
 */
@RestController
@RequestMapping("${shepherd.excel.task.endpoint-path:/excel/tasks}")
public class ExcelTaskController {

    private final AsyncExcelService service;

    public ExcelTaskController(AsyncExcelService service) {
        this.service = service;
    }

    @GetMapping("/{taskId}")
    public ExcelTaskVo get(@PathVariable String taskId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 匿名也当作未认证：否则所有匿名请求共用 "anonymousUser" 这一个 ownerId，归属判定形同虚设
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ExcelException(ExcelErrorCode.TASK_NOT_FOUND, "任务不存在");
        }
        return service.findForOwner(taskId, auth.getName())
                // 归属不匹配复用「不存在」，不返回 403
                .orElseThrow(() -> new ExcelException(ExcelErrorCode.TASK_NOT_FOUND, "任务不存在"));
    }
}
