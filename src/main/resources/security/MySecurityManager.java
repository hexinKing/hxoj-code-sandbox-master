import java.security.Permission;

/**
 * 我的安全管理器
 */
public class MySecurityManager extends SecurityManager {

    // 需要设置白名单，如果要做比较严格的权限限制，需要自己去判断哪些文件、包名需要允许读写。粒度太细了，难以精细化控制。
    // 安全管理器本身也是 Java 代码，也有可能存在漏洞。本质上还是程序层面的限制，没深入系统的层面。

    // 检查所有的权限
//    @Override
//    public void checkPermission(Permission perm) {
//        super.checkPermission(perm);
//    }

    // 检测程序是否可执行文件
    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限异常：" + cmd);
    }

    // 检测程序是否允许读文件
//    @Override
//    public void checkRead(String file) {
//        if (file.contains("C:\\code\\yuoj-code-sandbox")) {
//            return;
//        }
//        throw new SecurityException("checkRead 权限异常：" + file);
//    }

    // 检测程序是否允许写文件
    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite 权限异常：" + file);
    }

    // 检测程序是否允许删除文件
    @Override
    public void checkDelete(String file) {
        throw new SecurityException("checkDelete 权限异常：" + file);
    }

    // 检测程序是否允许连接网络
    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("checkConnect 权限异常：" + host + ":" + port);
    }
}
