package com.liblime.types;

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
