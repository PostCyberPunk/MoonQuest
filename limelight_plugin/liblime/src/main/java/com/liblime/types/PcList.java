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
    private ArrayList<ComputerObject> removeList = new ArrayList<>();

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
        return !updatedList.isEmpty()||!removeList.isEmpty();
    }

    public String getUpdatedList() {
        return getList(updatedList);
    }

    public String getRemoveList() {
        return getList(removeList);
    }

    private String getList(ArrayList<ComputerObject> list) {
        if (list.isEmpty())
            return null;
        var dataList = new ComputerObject.ComputerData[list.size()];
        for (int i = 0; i < list.size(); i++) {
            dataList[i] = list.get(i).ToData();
        }
        var result = new Gson().toJson(new ComputerDataWrapper(dataList));
        list.clear();
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

        if (itemList.remove(computer)) {
            removeList.add(computer);
            return true;
        } else {
            return false;
        }

    }

    public Object getItem(int i) {
        if (itemList.isEmpty())
            return null;
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

