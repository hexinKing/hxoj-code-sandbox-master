package com.hxoj.hxojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 判题模块提交代码请求对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteCodeRequest {
    /**
     * 题目输入用例列表
     */
    private List<String> inputList;

    /**
     * 题目的代码
     */
    private String code;

    /**
     * 编程语言
     */
    private String language;
}
