package com.cpa.tool;

import com.cpa.objectives.EdgeServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;


public class RandomServersGenerator {

//     generate from real world
    public List<EdgeServer> generateEdgeServerListFromRealWorldData(int serversNumber) {
        List<EdgeServer> servers = new ArrayList<>();
        List<EdgeServer> allServers = readServersFromCsv();

        for (int i = 0; i < serversNumber; i++) {
            int random = ThreadLocalRandom.current().nextInt(0, allServers.size());
            EdgeServer server = allServers.get(random);
            server.id = i;
            servers.add(server);
            allServers.remove(server);
        }

        return servers;
    }

    public List<EdgeServer> readServersFromCsv() {
        File file = new File("src/main/resources/dataset/site-optus-melbCBD.csv");

        List<EdgeServer> servers = new ArrayList<>();

        Scanner sc;
        try {
            sc = new Scanner(file);
            sc.nextLine();
            while (sc.hasNextLine()) {
                EdgeServer server = new EdgeServer();
                String[] info = sc.nextLine().replaceAll(" ", "").split(",");

                server.radius = Double.parseDouble(info[0]);
                server.lat = Double.parseDouble(info[1]);
                server.lng = Double.parseDouble(info[2]);
                servers.add(server);
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return servers;
    }

    //server not overlap
    public List<EdgeServer> generateEdgeServers(int serversNumber, double networkArea) {
        List<EdgeServer> servers = new ArrayList<>();
//        networkArea = serversNumber * 2;
        double interval = networkArea / serversNumber;
        for (int i = 0; i < serversNumber; i++) {
            EdgeServer server = new EdgeServer();
            server.id = i;
            server.fromArea = i * interval; // start area
            server.toArea = (i + 1) * interval; // end area
            servers.add(server);
        }

        return servers;
    }

}
