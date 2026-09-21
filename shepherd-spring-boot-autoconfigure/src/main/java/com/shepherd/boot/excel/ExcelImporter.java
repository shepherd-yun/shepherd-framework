package com.shepherd.boot.excel;

import cn.idev.excel.FastExcel;
import jakarta.validation.Validator;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 同步导入：流式解析 → 逐行校验 → 分批回调入库 → 汇总结果。
 *
 * <p>不提供「整表读成 List」的便捷方法——那是 OOM 诱饵。
 */
public class ExcelImporter {

    private static final Set<String> SUPPORTED = Set.of("xlsx", "xls", "csv");

    private final ShepherdExcelProperties properties;
    private final @Nullable Validator validator;

    public ExcelImporter(ShepherdExcelProperties properties, @Nullable Validator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    public <T> ExcelImportResult<T> read(MultipartFile file, Class<T> head, Consumer<List<T>> consumer) {
        checkExtension(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            return read(in, head, consumer);
        } catch (ExcelException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ExcelException(ExcelErrorCode.PARSE_FAILED, "读取上传文件失败", ex);
        }
    }

    public <T> ExcelImportResult<T> read(InputStream in, Class<T> head, Consumer<List<T>> consumer) {
        BatchReadListener<T> listener = new BatchReadListener<>(
                consumer, validator, properties.getBatchSize(), properties.getMaxRows());
        try {
            FastExcel.read(in, head, listener).sheet().doRead();
        } catch (ExcelException ex) {
            throw ex;
        } catch (Exception ex) {
            // FastExcel 会把 listener 抛出的异常包一层，拆出来才不会把 41301 降级成 50005
            if (ex.getCause() instanceof ExcelException wrapped) {
                throw wrapped;
            }
            throw new ExcelException(ExcelErrorCode.PARSE_FAILED, "Excel 解析失败", ex);
        }

        if (listener.total() == 0) {
            throw new ExcelException(ExcelErrorCode.EMPTY_FILE, "文件为空或无数据行");
        }
        return ExcelImportResult.of(listener.total(), listener.succeeded(), listener.errors(),
                properties.getInlineErrorLimit(), null);
    }

    private void checkExtension(@Nullable String fileName) {
        if (fileName == null) {
            throw new ExcelException(ExcelErrorCode.UNSUPPORTED_FORMAT, "缺少文件名，无法判断格式");
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(ext)) {
            throw new ExcelException(ExcelErrorCode.UNSUPPORTED_FORMAT,
                    "不支持的文件格式 '" + ext + "'，仅支持 xlsx / xls / csv");
        }
    }
}
