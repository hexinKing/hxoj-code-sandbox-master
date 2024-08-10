package com.hxoj.hxojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 */
@Data
public class ExecuteMessage {

    /**
     * 错误码
     */
    private Integer exitValue;
    /**
     * 正常执行结果
     */
    private String message;
    /**
     * 错误执行结果
     */
    private String errorMessage;
    /**
     * 执行时间
     */
    private Long time;
    /**
     * 内存使用
     */
    private Long memory;
}
