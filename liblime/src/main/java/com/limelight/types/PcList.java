package com.limelight.types;

import com.limelight.PcPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class PcList {
    private final ArrayList<ComputerObject> itemList = new ArrayList<>();
    public void addComputer(ComputerObject computer) {
        itemList.add(computer);
        sortList();
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

    public int getCount() {
        return itemList.size();
    }
}

