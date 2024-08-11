# HX-OJ在线判题系统————代码沙盒

### 完成代码沙盒的架构设计

整个项目主要是实现与判题服务解耦，通过API接口与判题服务进行交互，实现代码的编译、运行、结果返回等功能。

本次实现了使用原生的Java代码实现代码沙箱整个流程，包括代码编译、运行、结果返回等功能。
例如：  
1.将用户的代码写入文件中,把用户的代码进行隔离存放
2.编译代码得到class文件
3.执行class文件，并获取输出结果
4.收集整理输出结果
5.删除用户的代码文件、减少空间占用
6.返回执行结果，并进行特殊处理，提升程序健壮性,封装异常类

<<<<<<< HEAD
本次在原来代码的基础做了进一步的完善，对沙盒代码应对恶意代码的攻击，采用了一些安全措施，如：
1.解决无限睡眠（阻塞程序执行）问题，在执行class文件的时候，开启一个守护进程同步计时
2.解决无限占用空间（浪费系统内存）问题，在执行class文件的时候，限制最大内存为256m---“-Xmx256m”
3.编译代码时，使用白名单机制，只允许编译指定类型的文件，防止越界读取、写入、执行等操作文件
4.最后额外设置java安全管理器做特殊处理，限制程序对系统资源的访问，防止恶意代码对系统的破坏
java安全管理器优点：
权限控制灵活
实现简单
java安全管理器缺点：
如果要做比较严格的权限限制，需要自己去判断哪些文件、包名需要允许读写。粒度太细了，难以精细化控制。
安全管理器本身也是 Java 代码，也有可能存在漏洞。本质上还是程序层面的限制，没深入系统的层面

优化：使用Docker时限环境隔离（尚未完成）

=======
>>>>>>> hxoj-code-sandbox-master/master




