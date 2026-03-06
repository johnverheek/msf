package dev.msf.cli;

/**
 * The outcome of a single validation check.
 *
 * @param name   human-readable check name shown in validate output
 * @param status PASS, FAIL, or WARN
 * @param detail additional context appended after the check name, or empty string
 */
public record CheckResult(String name, Status status, String detail) {

    public enum Status {
        PASS,
        FAIL,
        WARN
    }

    public static CheckResult pass(String name) {
        return new CheckResult(name, Status.PASS, "");
    }

    public static CheckResult fail(String name, String detail) {
        return new CheckResult(name, Status.FAIL, detail);
    }

    public static CheckResult warn(String name, String detail) {
        return new CheckResult(name, Status.WARN, detail);
    }

    /** Render as a single output line: "  ✓ Name" / "  ✗ Name — detail" / "  ⚠ Name — detail" */
    public String toLine() {
        String symbol = switch (status) {
            case PASS -> "\u2713";
            case FAIL -> "\u2717";
            case WARN -> "\u26A0";
        };
        if (detail.isEmpty()) {
            return "  " + symbol + " " + name;
        }
        return "  " + symbol + " " + name + " \u2014 " + detail;
    }
}
