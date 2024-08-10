package com.hxoj.hxojcodesandbox;


import com.hxoj.hxojcodesandbox.model.ExecuteCodeRequest;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口定义
 */
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
