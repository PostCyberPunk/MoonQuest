package com.limelight.types;

import com.limelight.nvstream.http.ComputerDetails;

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
}
