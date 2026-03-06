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
 * </pre>
 */
@Command(
    name = "msf",
    subcommands = {InspectCommand.class, ValidateCommand.class, ConvertCommand.class},
    mixinStandardHelpOptions = true,
    version = "msf-cli 1.0",
    description = "MSF (Minecraft Structured Format) command-line tool."
)
public class MsfCli implements Runnable {

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
