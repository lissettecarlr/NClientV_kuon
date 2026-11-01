package com.maxwai.nclientv3.components.status;

import androidx.annotation.Nullable;

import com.maxwai.nclientv3.async.database.Queries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StatusManager {
    public static final String DEFAULT_STATUS = "None";
    private static final HashMap<String, Status> statusMap = new HashMap<>();

    public static Status getByName(String name) {
        return statusMap.get(name);
    }

    public static Status add(String name, int color) {
        return add(new Status(color, name));
    }

    static Status add(Status status) {
        Queries.StatusTable.insert(status);
        statusMap.put(status.name, status);
        return status;
    }

    /**
     * [FORK] Forget the statuses in memory and load them again from the database
     */
    public static void reload() {
        statusMap.clear();
        Queries.StatusTable.initStatuses();
    }

    /**
     * [FORK] the fixed statuses first, then alphabetical
     */
    private static int compare(String n1, String n2) {
        boolean f1 = FixedStatus.isFixed(n1), f2 = FixedStatus.isFixed(n2);
        if (f1 != f2) return f1 ? -1 : 1;
        return n1.compareToIgnoreCase(n2);
    }

    public static void remove(Status status) {
        if (FixedStatus.isFixed(status.name)) return;// [FORK]
        Queries.StatusTable.remove(status.name);
        statusMap.remove(status.name);
    }

    public static List<String> getNames() {
        List<String> st = new ArrayList<>(statusMap.keySet());
        st.sort(StatusManager::compare);// [FORK]
        st.remove(DEFAULT_STATUS);
        //st.add(0,DEFAULT_STATUS);
        return st;
    }

    public static List<Status> toList() {
        ArrayList<Status> statuses = new ArrayList<>(statusMap.values());
        statuses.sort((o1, o2) -> compare(o1.name, o2.name));// [FORK]
        statuses.remove(getByName(DEFAULT_STATUS));
        return statuses;
    }

    public static Status updateStatus(@Nullable Status oldStatus, String newName, int newColor) {
        if (oldStatus == null)
            return add(newName, newColor);
        if (FixedStatus.isFixed(oldStatus.name)) return oldStatus;// [FORK]
        Status newStatus = new Status(newColor, newName);
        Queries.StatusTable.update(oldStatus, newStatus);
        statusMap.remove(oldStatus.name);
        statusMap.put(newStatus.name, newStatus);
        return newStatus;
    }
}
