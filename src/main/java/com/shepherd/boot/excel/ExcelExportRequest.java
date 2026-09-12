package com.shepherd.boot.excel;

import java.util.List;
import java.util.function.IntFunction;

/**
 * 异步导出请求。
 *
 * @param pageFetcher 入参为 1-based 页码，返回空 List 即终止——保证内存恒定
 */
public record ExcelExportRequest<T>(String fileName, Class<T> head, IntFunction<List<T>> pageFetcher) {

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private String fileName = "export.xlsx";
        private Class<T> head;
        private IntFunction<List<T>> pageFetcher;

        public Builder<T> fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder<T> head(Class<T> head) {
            this.head = head;
            return this;
        }

        public Builder<T> pageFetcher(IntFunction<List<T>> pageFetcher) {
            this.pageFetcher = pageFetcher;
            return this;
        }

        public ExcelExportRequest<T> build() {
            if (head == null || pageFetcher == null) {
                throw new IllegalArgumentException("ExcelExportRequest 必须设置 head 与 pageFetcher");
            }
            return new ExcelExportRequest<>(fileName, head, pageFetcher);
        }
    }
}
