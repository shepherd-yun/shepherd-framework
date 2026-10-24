package com.shepherdyun.boot.excel;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 分批消费 + 逐行校验 + 错误收集。
 *
 * <p>回调抛异常时该批整体计失败但解析继续——单批写库失败不该让用户丢掉整个文件的解析结果。
 * 事务边界由业务方在回调实现上掌握，语义是「每批一个事务」，失败批次不回滚已提交的批次。
 */
class BatchReadListener<T> implements ReadListener<T> {

    private final Consumer<List<T>> consumer;
    private final @Nullable Validator validator;
    private final int batchSize;
    private final int maxRows;

    private final List<T> buffer;
    /** 与 buffer 平行：缓冲中每条数据对应的 Excel 行号，用于回调失败时定位。 */
    private final List<Integer> bufferRowNums;
    private final List<ExcelRowError> errors = new ArrayList<>();

    private long total;
    private long succeeded;

    BatchReadListener(Consumer<List<T>> consumer, @Nullable Validator validator,
                      int batchSize, int maxRows) {
        this.consumer = consumer;
        this.validator = validator;
        this.batchSize = batchSize;
        this.maxRows = maxRows;
        this.buffer = new ArrayList<>(batchSize);
        this.bufferRowNums = new ArrayList<>(batchSize);
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        if (total >= maxRows) {
            throw new ExcelException(ExcelErrorCode.TOO_MANY_ROWS,
                    "导入行数超出上限 " + maxRows + "，请拆分文件后重试");
        }
        total++;
        // 表头占第 1 行，第 n 条数据是第 n+1 行
        int rowNum = (int) total + 1;

        if (validator != null) {
            Set<ConstraintViolation<T>> violations = validator.validate(data);
            if (!violations.isEmpty()) {
                for (ConstraintViolation<T> v : violations) {
                    errors.add(new ExcelRowError(rowNum,
                            v.getPropertyPath().toString(), v.getMessage()));
                }
                return;
            }
        }

        buffer.add(data);
        bufferRowNums.add(rowNum);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        ImportHeadCheck.check(headMap, context);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        List<T> batch = List.copyOf(buffer);
        List<Integer> rowNums = List.copyOf(bufferRowNums);
        buffer.clear();
        bufferRowNums.clear();

        try {
            consumer.accept(batch);
            succeeded += batch.size();
        } catch (Exception ex) {
            String message = "入库失败：" + ex.getMessage();
            for (Integer rowNum : rowNums) {
                errors.add(new ExcelRowError(rowNum, "", message));
            }
        }
    }

    long total() {
        return total;
    }

    long succeeded() {
        return succeeded;
    }

    List<ExcelRowError> errors() {
        return List.copyOf(errors);
    }
}
