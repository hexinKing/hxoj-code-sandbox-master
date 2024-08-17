package com.hxoj.hxojcodesandbox;

import cn.hutool.core.io.FileUtil;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeRequest;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeResponse;
import com.hxoj.hxojcodesandbox.model.ExecuteMessage;
import com.hxoj.hxojcodesandbox.model.JudgeInfo;
import com.hxoj.hxojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 代码沙箱模版方法实现类
 */
@Slf4j
public class JavaCodeSandboxTemplate implements CodeSandbox {
    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    public static final Long TIME_OUT = 5000L;

    /**
     * java原生代码沙箱完整流程
     *
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        // 1.保存代码到文件中
        File userCodeFile = saveCodeToFile(code);
        // 2.编译代码得到class文件
        compileTheCode(userCodeFile);
        // 3.执行class文件，并获取输出结果
        List<ExecuteMessage> executeMessageArrayList = executeTheCode(userCodeFile, inputList);
        // 4.收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = collectOutput(executeMessageArrayList);
        // 5.删除用户的代码文件、减少空间占用
        Boolean deleteCodeFile = deleteCodeFile(userCodeFile);
        if (!deleteCodeFile) {
            log.error("删除用户代码文件失败");
        }
        return executeCodeResponse;
    }

    /**
     * 保存代码到文件中
     *
     * @param code
     */
    public File saveCodeToFile(String code) {
        // 1、将用户提交的代码保存为文件
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
        // 实际存放文件的目录
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 编译代码得到class文件
     *
     * @param userCodeFile
     */
    public void compileTheCode(File userCodeFile) {
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
                throw new RuntimeException("编译错误！");
            }
        } catch (IOException e) {
            throw new RuntimeException("编译代码异常", e);
        }
    }

    /**
     * 执行class文件，并获取输出结果
     *
     * @param userCodeFile
     * @param inputList
     */
    public List<ExecuteMessage> executeTheCode(File userCodeFile, List<String> inputList) {
        List<ExecuteMessage> executeMessageArrayList = new ArrayList<>();
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        for (String inputArgs : inputList) {
            // 解决无限占用空间（浪费系统内存）问题，在执行class文件的时候，限制最大内存为256m---“-Xmx256m”
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            // 实际情况下，不应该在主类（开发者自己写的程序）中做限制，只需要限制子程序的权限即可
            // 启动子进程执行命令时，设置安全管理器，而不是在外层设置（会限制住测试用例的读写和子命令的执行）
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s",
//                    userCodeParentPath,
//                    SECURITY_MANAGER_PATH,
//                    SECURITY_MANAGER_CLASS_NAME,
//                    inputArgs);
            // -----------------------------
            // java 的 -Djava.security.manager 选项只启用安全管理器，并不接受类名作为参数。你需要在代码中手动设置 SecurityManager
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager Main %s",
//                    userCodeParentPath,
//                    SECURITY_MANAGER_PATH,
//                    inputArgs);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                // 解决无限睡眠（阻塞程序执行）问题，在执行class文件的时候，开启一个守护进程同步计时
                new Thread(() -> {
                    try {
                        //让线程睡3秒，如果3秒后进程还未结束，则强制结束进程
                        Thread.sleep(TIME_OUT);
                        if (process.isAlive()) {
                            log.info("进程执行超时，强制结束进程");
                            process.destroy();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "执行");
                executeMessageArrayList.add(executeMessage);
                // 以交互式的方式执行进程并获取信息
//                ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(process, inputArgs);
                if (executeMessage.getExitValue() == 0) {
                    log.info("执行成功:" + executeMessage);
                } else {
                    log.info("执行失败:" + executeMessage);
                }
            } catch (IOException e) {
                throw new RuntimeException("执行代码异常", e);
            }
        }
        return executeMessageArrayList;
    }

    /**
     * 收集整理输出结果
     *
     * @param executeMessageArrayList
     * @return
     */
    public ExecuteCodeResponse collectOutput(List<ExecuteMessage> executeMessageArrayList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        // 遍历执行结果，将输出结果拼接到一起
        List<String> outputList = new ArrayList<>();
        List<Long> timeList = new ArrayList<>();
        List<Long> MemoryList = new ArrayList<>();
        JudgeInfo judgeInfo = new JudgeInfo();
        for (ExecuteMessage executeMessage1 : executeMessageArrayList) {
            // 如果executeMessage的errorMessage不为空，则说明执行失败，返回错误信息
            if (executeMessage1.getErrorMessage() != null) {
                executeCodeResponse.setMessage("代码沙盒执行错误，原因：" + executeMessage1.getErrorMessage());
                judgeInfo.setMessage("代码沙盒执行错误");
                // 设置状态码 3 表示用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            } else {
                executeCodeResponse.setMessage("代码沙盒执行成功");
                judgeInfo.setMessage("代码沙盒执行成功");
                // 设置状态码 1 表示代码沙盒执行成功
                executeCodeResponse.setStatus(1);
            }
            outputList.add(executeMessage1.getMessage());
            timeList.add(executeMessage1.getTime());
            if (executeMessage1.getMemory() != null) {
                MemoryList.add(executeMessage1.getMemory());
            }
        }
        // 设置输出结果
        executeCodeResponse.setOutputList(outputList);
        // 设置时间,获取最大的时间
        timeList.stream().max(Long::compareTo).ifPresent(time -> judgeInfo.setTime(time));
        // 设置内存,获取最大的内存
        MemoryList.stream().max(Long::compareTo).ifPresent(memory -> judgeInfo.setMemory(memory));
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 删除用户的代码文件、减少空间占用
     *
     * @param userCodeFile
     * @return
     */
    public Boolean deleteCodeFile(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParent();
        boolean del = false;
        if (userCodeFile.getParentFile() != null) {
            // 删除用户代码目录
            del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return del;
    }

    /**
     * 获取错误响应
     *
     * @param e
     * @return
     */
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
