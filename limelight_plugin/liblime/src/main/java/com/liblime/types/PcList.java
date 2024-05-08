package com.liblime.types;

import com.google.gson.Gson;
import com.limelight.LimeLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class PcList {
    private final ArrayList<ComputerObject> itemList = new ArrayList<>();
    private ArrayList<ComputerObject> updatedList = new ArrayList<>();

    public void addComputer(ComputerObject computer) {
        itemList.add(computer);
        sortList();
        updatedList.add(computer);
    }

    public void updateComputer(ComputerObject computer) {
        updatedList.add(computer);
    }

    public boolean needUpdate() {
        return !updatedList.isEmpty();
    }

    public String getUpdatedList() {
        if (updatedList.isEmpty()) {
            return null;
        }
        var dataList = new ComputerObject.ComputerData[updatedList.size()];
        for (int i = 0; i < updatedList.size(); i++) {
            dataList[i] = updatedList.get(i).ToData();
        }
        String result = new Gson().toJson(dataList);
        updatedList.clear();
        return result;
    }



    private void sortList() {
        Collections.sort(itemList, new Comparator<ComputerObject>() {
            @Override
            public int compare(ComputerObject lhs, ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(ComputerObject computer) {
        return itemList.remove(computer);
    }

    public Object getItem(int i) {
        return itemList.get(i);
    }

    public Object getItem(String uuid) {
        for (ComputerObject computerObject : itemList) {
            if (computerObject.details.uuid.equals(uuid)) {
                return computerObject;
            }
        }
        return null;
    }

    public int getCount() {
        return itemList.size();
    }
}

