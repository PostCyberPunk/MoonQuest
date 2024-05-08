package com.liblime.types;

import com.google.gson.Gson;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import java.io.Serializable;

public class ComputerObject {
    public ComputerDetails details;

    public ComputerObject(ComputerDetails details) {
        if (details == null) {
            throw new IllegalArgumentException("details must not be null");
        }
        this.details = details;
    }

    @Override
    public String toString() {
        return details.name;
    }

    public String ToData() {
        return new Gson().toJson(new ComputerData(details));
    }

    public static class ComputerData implements Serializable {
        public String uuid;
        public String name;
        public ComputerDetails.State state;
        public PairingManager.PairState pairState;

        public ComputerData(ComputerDetails details) {
            this.uuid = details.uuid;
            this.name = details.name;
            this.state = details.state;
            this.pairState = details.pairState;
        }
    }
}
