package com.shepherdyun.boot.excel;

import cn.idev.excel.annotation.ExcelProperty;
import com.shepherdyun.boot.serialno.SerialNoGenerator;
import com.shepherdyun.boot.serialno.SerialNoRule;
import com.shepherdyun.boot.storage.ObjectRef;
import com.shepherdyun.boot.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncExcelServiceTest {

    public static class Row {
        @ExcelProperty("姓名") private String name;

        public Row() {
        }

        public Row(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 内存版 store，比 mock 更直白——本测试要断言的是任务状态流转，不是调用次数。 */
    static class InMemoryStore implements ExcelTaskStore {
        final Map<String, ExcelTask> saved = new HashMap<>();
        final List<ExcelTaskStatus> statusTrail = new ArrayList<>();

        @Override
        public void save(ExcelTask task) {
            saved.put(task.id(), task);
            statusTrail.add(task.status());
        }

        @Override
        public Optional<ExcelTask> find(String id) {
            return Optional.ofNullable(saved.get(id));
        }
    }

    /** 只记录不执行：submit 的断言要看「刚提交」的状态，执行由测试显式直调 runExport/runImport。 */
    static class DeferredExecutor implements java.util.concurrent.Executor {
        final List<Runnable> queued = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            List<Runnable> pending = List.copyOf(queued);
            queued.clear();
            pending.forEach(Runnable::run);
        }
    }

    private InMemoryStore store;
    private ObjectStorage storage;
    private DeferredExecutor executor;
    private AsyncExcelService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        storage = mock(ObjectStorage.class);
        executor = new DeferredExecutor();
        when(storage.put(anyString(), any(byte[].class), anyString()))
                .thenReturn(mock(ObjectRef.class));
        when(storage.publicUrl(anyString()))
                .thenAnswer(inv -> "https://cdn.example.com/" + inv.getArgument(0));

        SerialNoGenerator serialNo = mock(SerialNoGenerator.class);
        when(serialNo.next(any(SerialNoRule.class))).thenReturn("EX202609130001");

        ShepherdExcelProperties props = new ShepherdExcelProperties();
        service = new AsyncExcelService(store, storage, serialNo,
                new ExcelImporter(props, null), props, executor);
    }

    private static ExcelExportRequest<Row> request(java.util.function.IntFunction<List<Row>> fetcher) {
        return ExcelExportRequest.<Row>builder()
                .fileName("订单.xlsx").head(Row.class).pageFetcher(fetcher).build();
    }

    @Test
    void submit_export_records_a_pending_task_owned_by_the_submitter() {
        String taskId = service.submitExport(request(pageNo -> List.of()), "alice");

        assertThat(taskId).isEqualTo("EX202609130001");
        ExcelTask task = store.find(taskId).orElseThrow();
        assertThat(task.status()).isEqualTo(ExcelTaskStatus.PENDING);
        assertThat(task.ownerId()).isEqualTo("alice");
        assertThat(task.type()).isEqualTo(ExcelTaskType.EXPORT);
    }

    /** 提交必须把活儿交给 executor，而不是在请求线程上同步跑完。 */
    @Test
    void submit_export_hands_the_work_to_the_executor() {
        service.submitExport(request(pageNo -> List.of()), "alice");

        assertThat(executor.queued).hasSize(1);
    }

    @Test
    void run_export_uploads_the_workbook_and_marks_the_task_succeeded() {
        String taskId = service.submitExport(
                request(pageNo -> pageNo == 1 ? List.of(new Row("张三")) : List.of()), "alice");

        executor.runAll();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(key.capture(), bytes.capture(), anyString());

        assertThat(key.getValue()).startsWith("excel/export/").contains(taskId);
        assertThat(new String(bytes.getValue(), 0, 2)).as("上传的是 xlsx（zip 魔数 PK）").isEqualTo("PK");

        ExcelTask task = store.find(taskId).orElseThrow();
        assertThat(task.status()).isEqualTo(ExcelTaskStatus.SUCCEEDED);
        assertThat(task.resultKey()).isEqualTo(key.getValue());
        assertThat(store.statusTrail).containsSubsequence(
                ExcelTaskStatus.PENDING, ExcelTaskStatus.SUCCEEDED);
    }

    @Test
    void run_export_marks_the_task_failed_when_the_fetcher_blows_up() {
        String taskId = service.submitExport(request(pageNo -> {
            throw new IllegalStateException("查库炸了");
        }), "alice");

        executor.runAll();

        ExcelTask task = store.find(taskId).orElseThrow();
        assertThat(task.status()).isEqualTo(ExcelTaskStatus.FAILED);
        assertThat(task.errorMessage()).contains("查库炸了");
    }

    @Test
    void find_for_owner_hides_other_peoples_tasks() {
        String taskId = service.submitExport(request(pageNo -> List.of()), "alice");

        assertThat(service.findForOwner(taskId, "alice")).isPresent();
        // 归属不匹配与不存在给同一个结果——403 等于确认「该 id 存在但不属于你」
        assertThat(service.findForOwner(taskId, "bob")).isEmpty();
        assertThat(service.findForOwner("EX-nope", "alice")).isEmpty();
    }

    @Test
    void vo_carries_a_download_url_instead_of_the_raw_object_key() {
        String taskId = service.submitExport(
                request(pageNo -> pageNo == 1 ? List.of(new Row("张三")) : List.of()), "alice");
        executor.runAll();

        ExcelTaskVo vo = service.findForOwner(taskId, "alice").orElseThrow();

        assertThat(vo.downloadUrl()).startsWith("https://cdn.example.com/excel/export/");
        assertThat(vo.status()).isEqualTo("SUCCEEDED");
    }

    /** 请求结束后 MultipartFile 的临时文件就没了，内容必须在 submit 返回前读出来。 */
    @Test
    void submit_import_reads_the_upload_before_returning() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        cn.idev.excel.FastExcel.write(out, Row.class).sheet("Sheet1").doWrite(List.of(new Row("张三")));
        var file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        List<Row> consumed = new ArrayList<>();
        String taskId = service.submitImport(file, Row.class, consumed::addAll, "alice");

        // 提交后把上传流清空，模拟请求结束——异步执行仍应拿到内容
        executor.runAll();

        assertThat(consumed).extracting(Row::getName).containsExactly("张三");
        ExcelTask task = store.find(taskId).orElseThrow();
        assertThat(task.type()).isEqualTo(ExcelTaskType.IMPORT);
        assertThat(task.status()).isEqualTo(ExcelTaskStatus.SUCCEEDED);
        assertThat(task.resultKey()).as("无错误行时不产出错误文件").isNull();
    }

    /** 有错误行时把它们写成 Excel 落对象存储，形成「改完重传」的闭环。 */
    @Test
    void failed_rows_are_written_back_as_an_error_workbook() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        cn.idev.excel.FastExcel.write(out, Row.class).sheet("Sheet1")
                .doWrite(List.of(new Row("张三"), new Row("李四")));
        var file = new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        String taskId = service.submitImport(file, Row.class, batch -> {
            throw new IllegalStateException("写库唯一键冲突");
        }, "alice");
        executor.runAll();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(key.capture(), bytes.capture(), anyString());
        assertThat(key.getValue()).startsWith("excel/error/").contains(taskId);

        var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(bytes.getValue()));
        assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("行号");
        assertThat(wb.getSheetAt(0).getLastRowNum()).as("表头 + 2 条错误行").isEqualTo(2);
        assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getStringCellValue())
                .contains("写库唯一键冲突");

        assertThat(store.find(taskId).orElseThrow().resultKey()).isEqualTo(key.getValue());
    }
}
