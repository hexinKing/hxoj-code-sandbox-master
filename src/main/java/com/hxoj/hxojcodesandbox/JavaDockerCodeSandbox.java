package com.hxoj.hxojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeRequest;
import com.hxoj.hxojcodesandbox.model.ExecuteCodeResponse;
import com.hxoj.hxojcodesandbox.model.ExecuteMessage;
import com.hxoj.hxojcodesandbox.model.JudgeInfo;
import com.hxoj.hxojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Docker代码沙箱实现类
 */
@Slf4j
public class JavaDockerCodeSandbox implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;
    private static final Boolean FIRST_INIT = true;


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        // 配置java安全管理器
//        System.setSecurityManager(new MySecurityManager());

        String code = executeCodeRequest.getCode();
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

        // 3. 把编译好的文件上传到容器环境内.创建容器，上传编译文件
        // 获取默认的dockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String imageName = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(imageName);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                log.info("拉取镜像异常");
                return getErrorResponse(e);
            }
        }
        log.info("镜像下载完成");

        // 创建一个可交互的容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageName);
        HostConfig hostConfig = new HostConfig();
        // 通过HostConfig的withMemory等方法，设置容器的最大内存和资源限制
        // 限制内存
        hostConfig.withMemory(100 * 1000 * 1000L);
        // 设置内存交换的值
        hostConfig.withMemorySwap(0L);
        // 限制CPU
        hostConfig.withCpuCount(1L);
        // 设置Linux安全配置
//        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        // 设置容器挂载目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true) // 安全设置， 禁用网络
                .withReadonlyRootfs(true)  // 安全设置， 限制用户不能向root根目录写入文件
                .withAttachStderr(true) // 开启控制台输入输出
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true) // 开启一个交互终端
                .exec();
        String containerId = createContainerResponse.getId();
        log.info("创建容器Id" + containerId);

        // 4. 在容器中执行代码，得到输出结果
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        // 执行命令并获取结果 ， Docker执行容器命令：docker exec [OPTIONS] CONTAINER COMMAND [ARG...]
        ArrayList<ExecuteMessage> executeMessageArrayList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true) // 开启控制台输出输出
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            String execCreateCmdResponseId = execCreateCmdResponse.getId();
            log.info("执行命令Id：" + execCreateCmdResponseId);
            if (execCreateCmdResponseId == null) {
                throw new RuntimeException("执行命令异常");
            }
            // 通过回调接口来获取程序的输出结果，并且通过 StreamType 来区分标准输出和错误输出
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            // 判断是否超时
            final boolean[] timeout = {true};
            // 启动执行命令，异步获取结果
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        log.info("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        log.info("输出结果：" + message[0]);
                    }
                    super.onNext(frame);
                }
            };

            final long[] maxMemory = {0L};
            // 获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    log.info("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            statsCmd.exec(statisticsResultCallback);

            // 执行启动命令
            try {
                // 解决无限睡眠（阻塞程序执行）问题并获取时间，超时控制
                stopWatch.start();
                dockerClient.execStartCmd(execCreateCmdResponseId)
                        .exec(execStartResultCallback)
//                        .awaitCompletion();
                        // 超时控制参数（单位：微秒）
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                log.info("执行命令耗时：" + time + "毫秒");
                // 关闭内存监听
                statsCmd.close();
            } catch (InterruptedException e) {
                return getErrorResponse(e);
            }
            // 封装数据
            if (timeout[0]) {
                executeMessage.setErrorMessage(errorMessage[0] + "同时程序执行超时");
            }
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageArrayList.add(executeMessage);
        }

        // 5.收集整理输出结果
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
            }
            outputList.add(executeMessage1.getMessage());
            timeList.add(executeMessage1.getTime());
            MemoryList.add(executeMessage1.getMemory());
        }
        // 设置输出结果
        executeCodeResponse.setOutputList(outputList);
        // 设置状态码 1 表示代码沙盒执行成功
        executeCodeResponse.setStatus(1);
        // 设置时间,获取最大的时间
        timeList.stream().max(Long::compareTo).ifPresent(time -> judgeInfo.setTime(time));
        // 设置内存,获取最大的内存
        MemoryList.stream().max(Long::compareTo).ifPresent(memory -> judgeInfo.setMemory(memory));
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 6.删除用户的代码文件、减少空间占用
        if (userCodeFile.getParentFile() != null) {
            // 删除用户代码目录
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    // 7.返回执行结果，并进行特殊处理，提升程序健壮性,封装异常类
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
