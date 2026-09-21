package com.shepherd.boot.excel;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;
import com.shepherd.boot.serialno.SerialNoGenerator;
import com.shepherd.boot.serialno.SerialNoRule;
import com.shepherd.boot.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 异步导入导出编排。
 *
 * <p>执行提交给注入的 {@link Executor}——装配时给的是 Boot 的 {@code applicationTaskExecutor}，
 * 其上挂着既有的 MdcTaskDecorator，因此异步任务的日志带得上 traceId。
 *
 * <p>刻意不用 {@code @Async} 注解：本类提交任务的入口（submitExport / submitImport）与执行方法
 * 在同一个类里，同类内部直调不走代理，{@code @Async} 会静默失效、整个导出在请求线程上同步跑完。
 */
public class AsyncExcelService {

    private static final Logger log = LoggerFactory.getLogger(AsyncExcelService.class);
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final SerialNoRule TASK_NO = SerialNoRule.builder()
            .prefix("EX").datePattern("yyyyMMdd").seqLength(4).build();

    private final ExcelTaskStore store;
    private final ObjectStorage storage;
    private final SerialNoGenerator serialNo;
    private final ExcelImporter importer;
    private final ShepherdExcelProperties properties;
    private final Executor executor;

    public AsyncExcelService(ExcelTaskStore store, ObjectStorage storage, SerialNoGenerator serialNo,
                             ExcelImporter importer, ShepherdExcelProperties properties,
                             Executor executor) {
        this.store = store;
        this.storage = storage;
        this.serialNo = serialNo;
        this.importer = importer;
        this.properties = properties;
        this.executor = executor;
    }

    public <T> String submitExport(ExcelExportRequest<T> request, String ownerId) {
        String taskId = serialNo.next(TASK_NO);
        store.save(ExcelTask.pending(taskId, ExcelTaskType.EXPORT, ownerId, request.fileName()));
        executor.execute(() -> runExport(taskId, request));
        return taskId;
    }

    public <T> String submitImport(MultipartFile file, Class<T> head,
                                   Consumer<List<T>> consumer, String ownerId) {
        String taskId = serialNo.next(TASK_NO);
        String fileName = file.getOriginalFilename() == null ? "import.xlsx" : file.getOriginalFilename();
        store.save(ExcelTask.pending(taskId, ExcelTaskType.IMPORT, ownerId, fileName));

        // 必须在返回前读出内容：请求结束后 MultipartFile 的临时文件会被清理，异步线程再读就是空的
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            store.find(taskId).ifPresent(t -> store.save(t.failed("读取上传文件失败：" + ex.getMessage())));
            throw new ExcelException(ExcelErrorCode.PARSE_FAILED, "读取上传文件失败", ex);
        }
        executor.execute(() -> runImport(taskId, bytes, head, consumer));
        return taskId;
    }

    <T> void runExport(String taskId, ExcelExportRequest<T> request) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long processed = 0;
            ExcelWriter writer = FastExcel.write(out, request.head()).build();
            try {
                WriteSheet sheet = FastExcel.writerSheet("Sheet1").build();
                for (int pageNo = 1; ; pageNo++) {
                    List<T> page = request.pageFetcher().apply(pageNo);
                    if (page == null || page.isEmpty()) {
                        break;
                    }
                    writer.write(page, sheet);
                    processed += page.size();
                    long done = processed;
                    store.find(taskId).ifPresent(t -> store.save(t.withProgress(done, -1)));
                }
            } finally {
                writer.finish();
            }

            String key = properties.getExportKeyPrefix() + taskId + ".xlsx";
            storage.put(key, out.toByteArray(), XLSX_CONTENT_TYPE);
            store.find(taskId).ifPresent(t -> store.save(t.succeeded(key)));
        } catch (Exception ex) {
            log.warn("Excel 导出任务 {} 失败", taskId, ex);
            store.find(taskId).ifPresent(t -> store.save(t.failed("导出失败：" + ex.getMessage())));
        }
    }

    <T> void runImport(String taskId, byte[] bytes, Class<T> head, Consumer<List<T>> consumer) {
        try {
            ExcelImportResult<T> result = importer.read(new ByteArrayInputStream(bytes), head, consumer);
            String errorKey = null;
            if (!result.errors().isEmpty()) {
                errorKey = properties.getErrorKeyPrefix() + taskId + ".xlsx";
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                FastExcel.write(out, ExcelErrorRow.class).sheet("错误行")
                        .doWrite(result.errors().stream().map(ExcelErrorRow::new).toList());
                storage.put(errorKey, out.toByteArray(), XLSX_CONTENT_TYPE);
            }
            String key = errorKey;
            store.find(taskId).ifPresent(t -> store.save(
                    t.withProgress(result.total(), result.total()).succeeded(key)));
        } catch (Exception ex) {
            log.warn("Excel 导入任务 {} 失败", taskId, ex);
            store.find(taskId).ifPresent(t -> store.save(t.failed("导入失败：" + ex.getMessage())));
        }
    }

    public Optional<ExcelTaskVo> findForOwner(String taskId, String ownerId) {
        return store.find(taskId)
                // 归属不匹配与不存在返回同一结果——403 会确认「该 id 存在但不属于你」
                .filter(task -> task.ownerId().equals(ownerId))
                .map(task -> ExcelTaskVo.from(task,
                        task.resultKey() == null ? null : storage.publicUrl(task.resultKey())));
    }
}
