package com.hxoj.hxojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java 原生代码沙箱实现类
 */
@Slf4j
public class JavaNativeCodeSandbox implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final String SECURITY_MANAGER_PATH = "E:\\JavaWeb_Project\\OJ_System\\hxoj-code-sandbox-master\\src\\main\\java\\com\\hxoj\\hxojcodesandbox\\security";
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
    // 超时时间
    public static final Long TIME_OUT = 3000L;
    // 敏感词汇黑名单
    private static final List<String> blackList = Arrays.asList("Files", "exec");
    // 初始化字典树
    private static final WordTree WORD_TREE = new WordTree();
    static {
        WORD_TREE.addWords(blackList);
    }


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        // 配置java安全管理器
//        System.setSecurityManager(new MySecurityManager());

        String code = executeCodeRequest.getCode();
        // 解决越界读写运行文件问题，设置黑白名单，加入敏感词汇。
        // 通过字典树检查代码是否有敏感词汇
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            log.info("包含禁止词：" + foundWord.getFoundWord());
            executeCodeResponse.setMessage("代码中包含禁止词汇：" + foundWord.getFoundWord());
            // 设置状态码 4 表示用户提交的代码中存在禁止词汇,建议直接封号
            executeCodeResponse.setStatus(4);
            return executeCodeResponse;
        }
        List<String> inputList = executeCodeRequest.getInputList();
//        String language = executeCodeRequest.getLanguage();
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
            return getErrorResponse(e);
        }

        // 3.执行class文件，并获取输出结果
        List<ExecuteMessage> executeMessageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            // 解决无限占用空间（浪费系统内存）问题，在执行class文件的时候，限制最大内存为256m---“-Xmx256m”
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);

            // 实际情况下，不应该在主类（开发者自己写的程序）中做限制，只需要限制子程序的权限即可
            // 启动子进程执行命令时，设置安全管理器，而不是在外层设置（会限制住测试用例的读写和子命令的执行）
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s",
//                    userCodeParentPath,
//                    SECURITY_MANAGER_PATH,
//                    SECURITY_MANAGER_CLASS_NAME,
//                    inputArgs);
            // -----------------------------
            // java 的 -Djava.security.manager 选项只启用安全管理器，并不接受类名作为参数。你需要在代码中手动设置 SecurityManager
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager Main %s",
                    userCodeParentPath,
                    SECURITY_MANAGER_PATH,
                    inputArgs);
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
//                log.info("执行完成:"+executeMessage);
                if (executeMessage.getExitValue() == 0) {
                    log.info("执行成功:" + executeMessage);
                } else {
                    log.info("执行失败:" + executeMessage);
                }
            } catch (IOException e) {
                return getErrorResponse(e);
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
