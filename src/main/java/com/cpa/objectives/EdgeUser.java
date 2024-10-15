package com.cpa.objectives;


import java.util.ArrayList;
import java.util.List;

public class EdgeUser {
    public double location;
    public double lat;
    public double lng;
    public int id;
    public List<Integer> nearEdgeServers = new ArrayList<>();
    public List<Integer> dataList = new ArrayList<>();
}
