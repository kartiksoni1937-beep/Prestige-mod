package dev.zprestige.prestige.render;

public final class ConfigReport {
    public final String operation;
    public int valueCount;
    public int sectionCount;
    public int errorCount;
    public int warningCount;

    public ConfigReport(String operationName) {
        this.operation = operationName;
    }

    public void warn(String path, String message, Object cause) {
        this.warningCount++;
    }

    public void error(String path, String message, Object cause) {
        this.errorCount++;
    }

    public void addValues(int count) {
        this.valueCount += count;
    }

    public boolean hasErrors() {
        return this.errorCount > 0;
    }

    public String getSummary() {
        return this.valueCount + "|" + this.warningCount + "|" + this.errorCount;
    }
}
