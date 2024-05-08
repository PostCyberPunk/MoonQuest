package com.liblime.types;

import com.google.gson.Gson;
import com.limelight.LimeLog;

import java.io.Serializable;
import java.lang.reflect.Array;
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
        for (ComputerObject computerObject : itemList) {
            if (computerObject.details.uuid.equals(computer.details.uuid)) {
                computerObject.details = computer.details;
                return;
            }
        }
        updatedList.add(computer);
    }

    public boolean needUpdate() {
        for (ComputerObject computerObject : itemList) {
            //TODO: that's very bad practise ,would some compute became null?
            if (computerObject.details.state == null || computerObject.details.pairState == null) {
                return false;
            }
        }
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
        String result = new Gson().toJson(new ComputerDataWrapper(dataList));
        updatedList.clear();
        return result;
    }

    private static class ComputerDataWrapper implements Serializable {
        public ComputerObject.ComputerData[] data;

        public ComputerDataWrapper(ComputerObject.ComputerData[] data) {
            this.data = data;
        }
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

