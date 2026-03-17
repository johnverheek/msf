package dev.msf.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point for the MSF command-line tool.
 *
 * <p>Usage:
 * <pre>
 *   msf inspect &lt;file&gt;
 *   msf validate &lt;file&gt;
 *   msf convert &lt;input&gt; &lt;output&gt;
 * </pre>
 */
@Command(
    name = "msf",
    subcommands = {InspectCommand.class, ValidateCommand.class, ConvertCommand.class},
    mixinStandardHelpOptions = true,
    version = "msf-cli 1.0.0",
    description = "MSF (Minecraft Structured Format) command-line tool."
)
public class MsfCli implements Runnable {

    /** CLI application version. Updated by the release process. */
    public static final String CLI_VERSION = "1.0.0";

    /** MSF binary format version this build was compiled against. */
    public static final String MSF_FORMAT_VERSION = "1.0";

    /**
     * Minecraft data version this CLI targets (MC 1.21.11).
     * Update when migrating the platform target to a new MC version.
     */
    public static final int MC_DATA_VERSION = 4325;

    /**
     * Prints the tool identification header to {@code stderr}.
     *
     * <p>All subcommands call this before producing any other output so that
     * automated tooling can identify the producing tool and its target environment.
     * Format: {@code msf-cli <version> | MSF format <major>.<minor> | MC data <data-version>}
     */
    public static void printHeader() {
        System.err.println("msf-cli " + CLI_VERSION
                + " | MSF format " + MSF_FORMAT_VERSION
                + " | MC data " + MC_DATA_VERSION);
    }

    @Override
    public void run() {
        // No subcommand supplied — print usage.
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MsfCli()).execute(args);
        System.exit(exitCode);
    }
}
