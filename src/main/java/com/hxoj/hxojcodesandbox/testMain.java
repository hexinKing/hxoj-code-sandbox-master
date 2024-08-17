package com.hxoj.hxojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeRequest;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * java原生代码测试入口
 */
public class testMain {
    public static void main(String[] args) {
        // java原生代码测试入口
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        // 切换为docker沙箱代码测试入口
//        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        List<String> inputStr = Arrays.asList("1 2", "3 4");
        executeCodeRequest.setInputList(inputStr);
        // 读取代码
        String code = ResourceUtil.readStr("tmpCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        // 异常测试 ,超长时间运行、内存溢出、越界读取文件、越界写入文件、越界执行文件等
//        String code = ResourceUtil.readStr("tmpCode/unsafeCode/WriteFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }
}
