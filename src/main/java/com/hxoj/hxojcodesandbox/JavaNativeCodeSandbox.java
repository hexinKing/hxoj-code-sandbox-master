package com.hxoj.hxojcodesandbox;

import cn.hutool.core.io.FileUtil;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeRequest;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeResponse;
import com.hxoj.hxojcodesandbox.model.ExecuteMessage;
import com.hxoj.hxojcodesandbox.model.JudgeInfo;
import com.hxoj.hxojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.ls.LSInput;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Java 原生代码沙箱实现类
 */
@Slf4j
public class JavaNativeCodeSandbox implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 获取当前的工作目录
        String property = System.getProperty("user.dir");
        // File.separator获取系统文件分隔符
        String globalCodePathName = property + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，没有则创建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 1.将用户的代码写入文件中,把用户的代码进行隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2.编译代码得到class文件
        // String.format()方法用于创建格式化的字符串
        String format = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsoluteFile());
        try {
            // javac执行编译命令
            Process process = Runtime.getRuntime().exec(format);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            if (executeMessage.getExitValue() == 0) {
                log.info("编译成功:" + executeMessage);
            } else {
                log.info("编译失败:" + executeMessage);
            }
        } catch (IOException e) {
            getErrorResponse(e);
        }

        // 3.执行class文件，并获取输出结果
        List<ExecuteMessage> executeMessageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "执行");
                executeMessageArrayList.add(executeMessage);
                // 以交互式的方式执行进程并获取信息
//                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(process, inputArgs);
//                log.info("执行完成:"+executeMessage);
                if (executeMessage.getExitValue() == 0) {
                    log.info("执行成功:" + executeMessage);
                } else {
                    log.info("执行失败:" + executeMessage);
                }
            } catch (IOException e) {
                getErrorResponse(e);
            }
        }

        // 4.收集整理输出结果
        // 遍历执行结果，将输出结果拼接到一起
        List<String> outputList = new ArrayList<>();
        List<Long> timeList = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageArrayList) {
            // 如果executeMessage的errorMessage不为空，则说明执行失败，返回错误信息
            if (executeMessage.getErrorMessage() != null) {
                executeCodeResponse.setMessage("代码沙盒执行错误，原因：" + executeMessage.getErrorMessage());
                // 设置状态码 3 表示用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            } else {
                executeCodeResponse.setMessage("代码沙盒执行成功");
            }
            outputList.add(executeMessage.getMessage());
            timeList.add(executeMessage.getTime());
        }
        executeCodeResponse.setOutputList(outputList);
        // 设置状态码 1 表示代码沙盒执行成功
        executeCodeResponse.setStatus(1);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("代码沙盒执行成功");
        // 设置内存,由于Java原生实现比较复杂,因为无法从Process对象中获取到子进程号,也不推荐在Java原生实现代码沙箱的过程中获取。
//        judgeInfo.setMemory();
        // 设置时间,获取最大的时间
        timeList.stream().max(Long::compareTo).ifPresent(time -> judgeInfo.setTime(time));
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5.删除用户的代码文件、减少空间占用
        if (userCodeFile.getParentFile() != null) {
            // 删除用户代码目录
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    // 6.返回执行结果，并进行特殊处理，提升程序健壮性,封装异常类
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 设置状态码 2 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
