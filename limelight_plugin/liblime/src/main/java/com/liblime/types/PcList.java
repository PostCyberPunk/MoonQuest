package com.liblime.types;

import android.text.style.UpdateAppearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class PcList {
    private final ArrayList<ComputerObject> itemList = new ArrayList<>();
    private ArrayList<ComputerObject> udpatedList = new ArrayList<>();

    public void addComputer(ComputerObject computer) {
        itemList.add(computer);
        sortList();
        udpatedList.add(computer);
    }

    public void updateComputer(ComputerObject computer) {
        udpatedList.add(computer);
    }

    public String[] getUpdatedList() {
        if (udpatedList.isEmpty()) {
            return null;
        }
        String[] updated = new String[udpatedList.size()];
        for (int i = 0; i < udpatedList.size(); i++) {
            updated[i] = udpatedList.get(i).ToData();
        }
        udpatedList.clear();
        return updated;
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
    public  Object getItem(String uuid){
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

