package com.shepherd.boot.excel;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "shepherd.excel")
public class ShepherdExcelProperties {

    /** 显式开启才装配——会拖进 POI + xmlbeans 约 18 个 jar。 */
    private boolean enabled = false;

    /** 导入行数硬上限，超出抛 41301。 */
    private int maxRows = 100_000;

    /** 分批回调大小。 */
    private int batchSize = 500;

    /**
     * 仅作建议值暴露给业务方判断走同步还是异步，Starter 自身不据此改变行为——
     * 自动分叉会让同一接口有时返回 xlsx 流、有时返回 JSON。
     */
    private int asyncThresholdRows = 5_000;

    /** 错误行超过此数则落文件并截断 errors。 */
    private int inlineErrorLimit = 200;

    private String exportKeyPrefix = "excel/export/";

    private String errorKeyPrefix = "excel/error/";

    private Template template = new Template();

    private Task task = new Task();

    @Getter
    @Setter
    public static class Template {
        /** 下拉生效的行数。不对整列加有效性——会明显拖累文件体积与 Excel 打开速度。 */
        private int defaultRows = 500;

        /** 超长 / 含逗号选项的承载 sheet，写入后标记为隐藏。 */
        private String hiddenSheetName = "__options";
    }

    @Getter
    @Setter
    public static class Task {
        /** 导出文件本就是临时产物，TTL 语义吻合。 */
        private Duration ttl = Duration.ofHours(24);

        private boolean endpointEnabled = true;

        private String endpointPath = "/excel/tasks";
    }
}
