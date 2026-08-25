package com.example.demo.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页响应体
 */
@Data
public class PageResult<T> {

    private int code;
    private String message;
    private List<T> data;
    private long total;
    private long page;
    private long pageSize;

    private PageResult() {}

    private PageResult(List<T> data, long total, long page, long pageSize) {
        this.code = 200;
        this.message = "success";
        this.data = data;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> data, long total, long page, long pageSize) {
        return new PageResult<>(data, total, page, pageSize);
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
