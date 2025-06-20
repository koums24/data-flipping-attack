import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class ThreadStart {
    public static void main(String[] args) {
        // 1. 先启动EdgeServer
        EdgeServer edgeServer = new EdgeServer();
        edgeServer.id = 1;
        edgeServer.edgeServerUrls = Arrays.asList("http://localhost:5001");

        edgeServer.startHttpServer(5001); // 启动HTTP服务器

        // 2. 等待服务器启动
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 2. 启动EdgeUser
        String edgeServerUrl = "http://localhost:5001";
        int numberOfUsers = 200;
        int requestsPerUser = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfUsers);

        // 直接启动n个用户线程
        for (int i = 0; i < numberOfUsers; i++) {

            final int userId = i;
            executor.submit(() -> {
                EdgeUser user = new EdgeUser(userId, edgeServerUrl);
                user.sendRequestsToEdgeServer(requestsPerUser);
            });
        }

        System.out.println("启动了 " + numberOfUsers + " 个用户线程向 " + edgeServerUrl + " 发送请求");

        // 运行完成后关闭
        executor.shutdown();
    }
}
