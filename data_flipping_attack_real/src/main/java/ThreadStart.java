import com.cpa.objectives.EdgeServer;
import com.cpa.objectives.EdgeUser;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class ThreadStart {
    public static void main(String[] args) {
        EdgeServer edgeServer = new EdgeServer();
        edgeServer.id = 1;
        edgeServer.edgeServerUrls = Arrays.asList("http://localhost:5001");

        edgeServer.startHttpServer(5001);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String edgeServerUrl = "http://localhost:5001";
        int numberOfUsers = 200;
        int requestsPerUser = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfUsers);

        for (int i = 0; i < numberOfUsers; i++) {

            final int userId = i;
            executor.submit(() -> {
                EdgeUser user = new EdgeUser(userId, edgeServerUrl);
                user.sendRequestsToEdgeServer(requestsPerUser);
            });
        }


        executor.shutdown();
    }
}
