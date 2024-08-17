package com.hxoj.hxojcodesandbox.controller;

import com.hxoj.hxojcodesandbox.JavaDockerCodeSandbox;
import com.hxoj.hxojcodesandbox.JavaNativeCodeSandbox;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeRequest;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class JavaCodeSandboxController {

    // 定义鉴权请求头和密钥,实现调用安全性校验
    private static final String AUTH_REQUEST_HEADER = "auth";
    private static final String AUTH_REQUEST_SECRET = "secretKey";

    // Docker代码沙箱
    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;
    // Java原生代码沙箱
    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    /**
     * 执行Java原生代码沙箱
     */
    @PostMapping("/javaNativeCodeSandbox")
    public ExecuteCodeResponse executeJavaCodeSandbox(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                                      HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new IllegalArgumentException("executeCodeRequest cannot be null");
        }
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        return executeCodeResponse;
    }

    /**
     * 执行Docker代码沙箱
     */
    @PostMapping("/javaDockerCodeSandbox")
    public ExecuteCodeResponse executeDockerCodeSandbox(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
                                                        HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new IllegalArgumentException("executeCodeRequest cannot be null");
        }
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        return executeCodeResponse;
    }

}
