package com.shepherdyun.boot.excel;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.IntFunction;

/**
 * 导出与模板下载的 Web 胶水。
 *
 * <p>方法签名一律是 {@code void} + {@link HttpServletResponse}。这种方法会让 Spring 置
 * {@code requestHandled=true}，根本不进入 ResponseBodyAdvice，因此<b>不需要</b>标
 * {@code @NoResultWrap}——该行为由 ExcelResponsesWrapBypassTest 锁定。
 */
public final class ExcelResponses {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 模板承载超长 / 含逗号选项的隐藏 sheet 名，与 shepherd.excel.template.hidden-sheet-name 的默认值一致。 */
    private static final String DEFAULT_HIDDEN_SHEET_NAME = "__options";

    private ExcelResponses() {
    }

    public static <T> void write(HttpServletResponse response, String fileName,
                                 Class<T> head, List<T> data) {
        writeStream(response, fileName, head, pageNo -> pageNo == 1 ? data : List.of());
    }

    public static <T> void writeStream(HttpServletResponse response, String fileName,
                                       Class<T> head, IntFunction<List<T>> pageFetcher) {
        prepare(response, fileName);
        try {
            // autoCloseStream(false)：出错时流还开着才能 reset 回落成 JSON
            ExcelWriter writer = FastExcel.write(response.getOutputStream(), head)
                    .autoCloseStream(false)
                    .build();
            try {
                WriteSheet sheet = FastExcel.writerSheet("Sheet1").build();
                for (int pageNo = 1; ; pageNo++) {
                    List<T> page = pageFetcher.apply(pageNo);
                    if (page == null || page.isEmpty()) {
                        break;
                    }
                    writer.write(page, sheet);
                }
            } finally {
                writer.finish();
            }
            response.getOutputStream().flush();
        } catch (Exception ex) {
            reset(response);
            throw new ExcelException(ExcelErrorCode.WRITE_FAILED, "导出 Excel 失败：" + fileName, ex);
        }
    }

    /**
     * 生成导入模板。{@code options.rows()} 未设置时回落到 {@link ExcelTemplateOptions#DEFAULT_ROWS}；
     * 要让行数随 {@code shepherd.excel.template.default-rows} 走，用带 {@code defaultRows} 的重载。
     */
    public static void writeTemplate(HttpServletResponse response, String fileName,
                                     Class<?> head, ExcelTemplateOptions options) {
        writeTemplate(response, fileName, head, options, ExcelTemplateOptions.DEFAULT_ROWS);
    }

    /**
     * @param defaultRows options.rows() 未设置时使用的行数，由调用方（通常是配置）传入
     */
    public static void writeTemplate(HttpServletResponse response, String fileName,
                                     Class<?> head, ExcelTemplateOptions options, int defaultRows) {
        writeTemplate(response, fileName, head, options, defaultRows, DEFAULT_HIDDEN_SHEET_NAME);
    }

    public static void writeTemplate(HttpServletResponse response, String fileName,
                                     Class<?> head, ExcelTemplateOptions options,
                                     int defaultRows, String hiddenSheetName) {
        int rows = options.rows() == ExcelTemplateOptions.ROWS_UNSET ? defaultRows : options.rows();
        prepare(response, fileName);
        try {
            FastExcel.write(response.getOutputStream(), head)
                    .autoCloseStream(false)
                    .registerWriteHandler(new DropdownSheetWriteHandler(
                            head, options.dropdowns(), rows, hiddenSheetName))
                    .sheet("模板")
                    .doWrite(List.of());
            response.getOutputStream().flush();
        } catch (ExcelException ex) {
            reset(response);
            throw ex;
        } catch (Exception ex) {
            reset(response);
            // 下拉字段名写错时 FastExcel 会把 ExcelException 包进自己的异常，需拆出来保留 40007
            if (ex.getCause() instanceof ExcelException wrapped) {
                throw wrapped;
            }
            throw new ExcelException(ExcelErrorCode.WRITE_FAILED, "生成导入模板失败：" + fileName, ex);
        }
    }

    private static void prepare(HttpServletResponse response, String fileName) {
        // 不设 characterEncoding：xlsx 是二进制，charset 无意义且会被 Servlet 容器拼进 Content-Type
        response.setContentType(XLSX_CONTENT_TYPE);
        // RFC 5987：URLEncoder 把空格编成 "+"，但 RFC 要求 %20
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
    }

    /** reset 只在尚未 flush 时有效；大导出一旦开始 flush 就无法回落，属已接受的限制。 */
    private static void reset(HttpServletResponse response) {
        try {
            response.reset();
        } catch (IllegalStateException ignored) {
            // 已 flush，无法回落——异常仍会抛出并记录日志
        }
    }
}
