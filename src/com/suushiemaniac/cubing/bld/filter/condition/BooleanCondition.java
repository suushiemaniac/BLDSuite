package com.suushiemaniac.cubing.bld.filter.condition;

public class BooleanCondition {
    public static BooleanCondition YES() {
        return new BooleanCondition(true, true);
    }

    public static BooleanCondition NO() {
        return new BooleanCondition(false, true);
    }

    public static BooleanCondition UNIMPORTANT() {
        return new BooleanCondition(false, false);
    }

    public static BooleanCondition MAYBE() {
        return BooleanCondition.UNIMPORTANT();
    }

    private boolean truthValue, isImportant;

    private BooleanCondition(boolean truthValue, boolean isImportant) {
        this.truthValue = truthValue;
        this.isImportant = isImportant;
    }

    public void setImportant(boolean important) {
        this.isImportant = important;
    }

    public boolean isImportant() {
        return this.isImportant;
    }

    public boolean getValue() {
        return this.truthValue;
    }

    public void setValue(boolean newValue) {
        this.truthValue = newValue;
    }

    public boolean getPositive() {
        return !this.isImportant || this.truthValue;
    }

    public boolean getNegative() {
        return this.isImportant && this.truthValue;
    }

    public boolean evaluatePositive(boolean compareTo) {
        return !this.isImportant || this.truthValue == compareTo;
    }

    public boolean evaluateNegative(boolean compareTo) {
        return this.isImportant && this.truthValue == compareTo;
    }

    @Override
    public String toString() {
        return this.isImportant ? String.valueOf(this.truthValue) : "unimportant";
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof BooleanCondition) {
            BooleanCondition bc = (BooleanCondition) other;

            return this.truthValue == bc.truthValue
                    && this.isImportant == bc.isImportant;
        } else {
            return false;
        }
    }
}
