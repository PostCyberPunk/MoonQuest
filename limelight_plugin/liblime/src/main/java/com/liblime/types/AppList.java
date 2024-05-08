package com.liblime.types;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppList {
    private ArrayList<AppObject> allApps = new ArrayList<>();

    private static void sortList(List<AppObject> list) {
        Collections.sort(list, new Comparator<AppObject>() {
            @Override
            public int compare(AppObject lhs, AppObject rhs) {
                return lhs.app.getAppName().toLowerCase().compareTo(rhs.app.getAppName().toLowerCase());
            }
        });
    }

    public void addApp(AppObject app) {
        // Always add the app to the all apps list
        allApps.add(app);

        sortList(allApps);
    }

    public String getUpdatedList() {
        if (allApps.isEmpty()) {
            return null;
        }
        var dataList = new AppObject.AppData[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            dataList[i] = allApps.get(i).ToData();
        }
        return new Gson().toJson(new AppDataWrapper(dataList));
    }

    public static class AppDataWrapper {
        public AppObject.AppData[] data;

        public AppDataWrapper(AppObject.AppData[] data) {
            this.data = data;
        }
    }

    public void removeApp(AppObject app) {
        allApps.remove(app);
    }

    public Object getItem(int position) {
        return allApps.get(position);
    }

    public int getCount() {
        return allApps.size();
    }
}
