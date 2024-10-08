package com.hxoj.hxojcodesandbox;

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.hxoj.hxojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Docker代码沙箱实现类
 */
@Component
@Slf4j
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {
    private static final Boolean FIRST_INIT = true;

    /**
     * 对JavaCodeSandboxTemplate的方法进行重写，实现Docker代码沙箱的执行功能
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> executeTheCode(File userCodeFile, List<String> inputList) {
        // 1. 准备工作，拉取镜像，创建容器
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
                throw new RuntimeException("拉取镜像异常");
            }
        }
        log.info("镜像下载完成");

        // 2.创建一个可交互的容器
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
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

        // 3.在容器中执行代码，得到输出结果
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
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                log.info("执行命令耗时：" + time + "毫秒");
                // 关闭内存监听
                statsCmd.close();
            } catch (InterruptedException e) {
                throw new RuntimeException("执行命令异常");
            }
            // 4.封装数据
            if (timeout[0]) {
                executeMessage.setErrorMessage(errorMessage[0] + "同时程序执行超时");
            }
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageArrayList.add(executeMessage);
        }
        // 5.删除容器，释放资源
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        return executeMessageArrayList;
    }
}