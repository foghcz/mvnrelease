import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

/**
 * MvnRelease is a utility script for managing Maven project releases and development versions.
 * It automates the process of creating release branches, updating versions, and managing Git operations.
 * The script can be run interactively or with command-line arguments.
 * It's a replacement for command-line batch utility.
 *
 * Execution:
 *   java mvnrelease.java [operation] [version] [options]
 *
 * @author Mojmir Nebel
 */
public class mvnrelease {

    // Configuration
    private enum Configuration {
        APP_VERSION("1.0"),
        WORKING_DIR("./"),
        COMMAND_MVN("mvn"),
        COMMAND_MVN_ADDITIONAL_PARAMS(""), // additional parameters for mvn command, e.g. memory settings, it's parsed by spaces, do not use "" strings
        COMMAND_GIT("git"),

        // git-related:
        BRANCH_RELEASE_PATTERN("release/%s"),
        BRANCH_DEVELOP("develop"),
        TAG_RELEASE_PATTERN("v%s"),
        COMMIT_MESSAGE_RELEASE("[release-script] new branch for release %s"),
        COMMIT_MESSAGE_DEVELOPMENT("[release-script] new development version %s"),
        COMMIT_MESSAGE_BUGFIX("[release-script] new release version %s")
        ;
        private String config;
        Configuration(String config) {
            this.config = config;
        }

        @Override
        public String toString() {
            return config;
        }
        public String get() {
            return config;
        }
    }

    private static final String CONSOLE_SEPARATOR = "-----------------------------------------------";

    /**
     * This allows custom format of release branch version format
     */
    private static Function<VersionInfo, String> releaseBranchVersionFunction = (v) -> v.major + "." + v.minor;

    /**
     * Debug mode enabler.
     */
    private static boolean debugEnabled = false;

    /**
     * Prints usage information for the script.
     */
    private static void printUsage(VersionInfo versionInfo) {
        System.out.println("Automatic release script using maven and git.");
        System.out.println("Usage:");
        System.out.println(" MvnRelease [operation] [version] [options]");
        System.out.println("\tAvailable operations:");
        for (Operation value : Operation.values()) {
            System.out.println("\t\t[" + value.shortCut + "] " + value.help);
        }
        System.out.println("\t[version]");
        System.out.println("\t\tdesired new version, should have -SNAPSHOT suffix when run with 'develop' operation");
        System.out.println("\t[options]");
        System.out.println("\t\t--debug   - enable debug output");
        System.out.println("\t\t--confirm - enables confirmation dialog before running the process");
        System.out.println("\t\t--help    - prints this info");
        System.out.println("\nExamples:");
        System.out.println("\tjava mvnrelease.java release " + suggestVersion(Operation.RELEASE, versionInfo));
        System.out.println("\tjava mvnrelease.java dev " + suggestVersion(Operation.DEV, versionInfo));
        System.out.println("\tjava mvnrelease.java bugfix " + suggestVersion(Operation.BUGFIX, versionInfo));
    }

    /**
     * Gets the value of a specific tag from the pom.xml file.
     * @param doc Document object representing the parsed pom.xml
     * @param tagName Name of the tag to search for
     * @return The text content of the tag if found, otherwise null
     */
    private static String getProjectTagValue(Document doc, String tagName) {
        var nodeList = doc.getElementsByTagName(tagName);
        // iterate through the NodeList to find the project [tag] value
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getParentNode().getNodeName().equals("project")) {
                // Found the project version
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    /**
     * Parses the pom.xml file and extracts project information.
     * @return MavenInfo object containing groupId, artifactId, version, and name.
     */
    private static MavenInfo parsePom() {
        try {
            var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new File(Configuration.WORKING_DIR + "pom.xml"));
            doc.getDocumentElement().normalize();

            var version = getProjectTagValue(doc, "version");
            var artifactId = getProjectTagValue(doc, "artifactId");
            var groupId = getProjectTagValue(doc, "groupId");
            var name = getProjectTagValue(doc, "name");

            if (version == null || artifactId == null || groupId == null) {
                printError("pom.xml parsing failed: missing required tags", null, true);
            }
            return new MavenInfo(groupId, artifactId, version, name);
        } catch (Exception e) {
            printError("pom.xml parsing failed", e, true);
            return null; // never happens
        }
    }

    /**
     * Runs maven command.
     */
    private static int runMavenCommand(String... params) {
        // Run maven command with additional parameters if specified
        if (!Configuration.COMMAND_MVN_ADDITIONAL_PARAMS.get().isEmpty()) {
            var paramsList = new ArrayList<>(List.of(params));
            paramsList.addAll(List.of(
                    Configuration.COMMAND_MVN_ADDITIONAL_PARAMS.get()
                            .trim()
                            .replaceAll("\\s+", " ")
                            .split(" ")));
            return runCommand(Configuration.COMMAND_MVN.get(), debugEnabled, paramsList.toArray(new String[0]));
        } else {
            return runCommand(Configuration.COMMAND_MVN.get(), debugEnabled, params);
        }
    }

    /**
     * Runs a command in the working directory and prints the result if debug is enabled.
     * @param command
     * @param params
     * @return
     */
    private static int runCommand(String command, String... params) {
        return runCommand(command, debugEnabled, params);
    }

    /**
     * Runs a command in the working directory and optionally prints the result.
     * @param command
     * @param printResult
     * @param params
     * @return exit code of the command
     */
    private static int runCommand(String command, boolean printResult, String... params) {
        try {
            if (debugEnabled) {
                System.out.println("[DEBUG] Running command: " + command + " " + Arrays.toString(params));
            }
            // Create a ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.directory(new File(Configuration.WORKING_DIR.get()));
            var commandList = new ArrayList<String>();
            commandList.add(command);
            commandList.addAll(List.of(params));
            processBuilder.command(commandList);

            // Start the process
            Process process = processBuilder.start();

            if (printResult) {
                // Read the output of the command
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            // Wait for the process to complete and get the exit code
            int exitCode = process.waitFor();
            return exitCode;
        } catch (Exception e) {
            printError("Failed to run " + command + " command: " + params, e, true);
            return -1; // should never happen
        }
    }

    /**
     * Gets the current branch name from the Git repository.
     * @return The name of the current branch, or an empty string if it fails.
     */
    private static String getCurrentBranch() {
        try {
            // Run the Git command to get the current branch name
            ProcessBuilder processBuilder = new ProcessBuilder(Configuration.COMMAND_GIT.get(), "rev-parse", "--abbrev-ref", "HEAD");
            processBuilder.directory(new File(Configuration.WORKING_DIR.get()));
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            printError("Failed to get current branch name", e, false);
        }
        return "";
    }

    /**
     * Prints an error message for the user and optionally exits the script.
     * @param error
     * @param e
     * @param fail
     */
    private static void printError(String error, Exception e, boolean fail) {
        if (debugEnabled) {
            System.err.println("[ERROR] " + error);
            if (e != null) {
                e.printStackTrace();
            }
        } else {
            System.err.print("[ERROR] " + error);
            if (e != null) {
                System.err.println(":: " + e.getMessage());
            } else {
                System.err.println();
            }
        }
        if (fail) {
            System.exit(1);
        }
    }

    /**
     * Prints a warning message for the user if something didn't go as expected.
     * @param warning
     * @param e
     */
    private static void printWarning(String warning, Exception e) {
        if (debugEnabled) {
            System.err.println("[WARN] " + warning);
            if (e != null) {
                e.printStackTrace();
            }
        } else {
            System.err.println("[WARN] " + warning);
        }
    }

    /**
     * Asks user for the desired operation to perform base on the list of supported operations.
     * 'x' is used to exit the script.
     * @return
     */
    private static Operation askOperation() {
        var console = new Scanner(System.in);
        System.out.println("What would you like to perform:");
        for (Operation value : Operation.values()) {
            System.out.println("\t[" + value.shortCut + "]\t" + value.help);
        }
        System.out.println("\t-------------");
        System.out.println("\t[x]\texit");
        String action = "";
        Operation operation = null;
        while (operation == null) {
            System.out.print("Choice: ");
            action = console.nextLine().trim();
            for (Operation value : Operation.values()) {
                if (action.equalsIgnoreCase(value.shortCut) || action.equalsIgnoreCase(value.toString())) {
                    operation = value;
                    break;
                }
            }
            if (action.equalsIgnoreCase("x")) {
                System.exit(0);
            }
            if (operation == null) {
                System.out.println("Unknown operation, please try again.");
            }
        }
        return operation;
    }

    /**
     * Suggests a version based on the current version and type of operation.
     * @param operation
     * @param versionInfo
     * @return
     */
    private static String suggestVersion(Operation operation, VersionInfo versionInfo) {
        try {
            switch (operation) {
                case RELEASE -> {
                    // suggest version based on current version
                    return versionInfo.major + "." + versionInfo.minor + "." + versionInfo.patch;
                }
                case DEV -> {
                    // suggest next development version
                    return versionInfo.major + "." + (Integer.parseInt(versionInfo.minor)+1) + "." + versionInfo.patch + "-SNAPSHOT";
                }
                case BUGFIX -> {
                    // suggest bugfix version, should be run on release branch
                    return versionInfo.major + "." + versionInfo.minor + "." + (Integer.parseInt(versionInfo.patch) + 1);
                }
                default -> {
                    // no suggestion for other operations
                    return "";
                }
            }
        } catch (NumberFormatException nfe) {
                printWarning("Unable to parse version number, suggestion skipped", nfe);
        }
        return "";
    }

    /**
     * Asks user for the desired version. It provides suggestion if available.
     * @param suggestedVersion
     * @return
     */
    private static String askVersion(String suggestedVersion) {
        var console = new Scanner(System.in);
        if (!suggestedVersion.isEmpty()) {
            System.out.print("What is the desired version [" + suggestedVersion + "]: ");
        } else {
            System.out.print("What is the desired version: ");
        }
        String version = null;
        while (version == null) {
            version = console.nextLine().trim();
            if ("".equals(version)) {
                if (!suggestedVersion.isEmpty()) {
                    version = suggestedVersion;
                } else {
                    version = null;
                }
            }
        }
        return version;
    }

    /**
     * Asks user for confirmation of the operation.
     * @param interactive
     */
    private static void userConfirm(boolean interactive) {
        if (interactive) {
            // ask user if it's ok and continue
            var console = new Scanner(System.in);
            System.out.print("Is this correct? [y/N]: ");
            var confirm = console.nextLine().trim();
            if (!"y".equalsIgnoreCase(confirm)) {
                System.out.println("Aborting...");
                System.exit(1);
            }
            System.out.println(CONSOLE_SEPARATOR);
        }
    }

    /**
     * Runs the Maven command to update the version in the pom.xml files.
     * @param version
     */
    private static void runMavenVersionUpdate(String version) {
        System.out.print("Running maven... ");
        runMavenCommand("--batch-mode", "versions:set", "-DnewVersion=" + version, "-DgenerateBackupPoms=false", "-DrunInReleaseMode=true");
        runMavenCommand("--batch-mode", "versions:commit");
        System.out.println("done");
    }

    /**
     * Runs the release process.
     * This includes updating the version in pom.xml files and creating a new brachch for the release.
     *
     * @param version
     * @param interactive
     */
    private static void runRelease(String version, boolean interactive) {
        // parse version for branch name
        if (version.endsWith("-SNAPSHOT")) {
            printWarning("Release version should not end with -SNAPSHOT suffix!", null);
        }
        var versionInfo = new VersionInfo(version);
        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("::::::: Performing release :::::::");
        var branchVersion = releaseBranchVersionFunction.apply(versionInfo);
        var branchName = String.format(Configuration.BRANCH_RELEASE_PATTERN.get(), branchVersion);
        System.out.println("\tRelease version     : " + version);
        System.out.println("\tCurrent branch name : " + getCurrentBranch());
        System.out.println("\tBranch to be created: " + branchName);
        System.out.println(CONSOLE_SEPARATOR);
        userConfirm(interactive);

        runMavenVersionUpdate(version);

        System.out.print("Running git... ");
        runCommand(Configuration.COMMAND_GIT.get(), "checkout", "-b", branchName);
        runCommand(Configuration.COMMAND_GIT.get(), "commit","-am", String.format(Configuration.COMMIT_MESSAGE_RELEASE.get(), branchVersion));
        runCommand(Configuration.COMMAND_GIT.get(), "tag", String.format(Configuration.TAG_RELEASE_PATTERN.get(), version));
        System.out.println("done");

        System.out.println("::::::: Release complete :::::::");
        System.out.println("Please verify the release and push the changes to the remote repository:");
        System.out.println("git push origin " + branchName);
        System.out.println(CONSOLE_SEPARATOR);
    }

    /**
     * Runs the development version update.
     * It also checks if the current branch is the develop branch and if the version ends with -SNAPSHOT suffix.
     *
     * @param version
     * @param interactive
     */
    private static void runDevelop(String version, boolean interactive) {
        // parse version for branch name
        var currentBranch = getCurrentBranch();
        if (!currentBranch.equals(Configuration.BRANCH_DEVELOP.get())) {
            printWarning("Development version should be updated on " + Configuration.BRANCH_DEVELOP.get() + " branch!", null);
        }
        if (!version.endsWith("-SNAPSHOT")) {
            printWarning("Development version should end with -SNAPSHOT suffix!", null);
        }
        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("::::::: Performing development version update :::::::");
        System.out.println("\tCurrent branch name : " + currentBranch);
        System.out.println("\tNew development version: " + version);
        System.out.println(CONSOLE_SEPARATOR);
        userConfirm(interactive);

        runMavenVersionUpdate(version);

        System.out.print("Running git... ");
        runCommand(Configuration.COMMAND_GIT.get(), "commit","-am", String.format(Configuration.COMMIT_MESSAGE_DEVELOPMENT.get(), version));
        System.out.println("done");

        System.out.println("::::::: Development version update complete :::::::");
        System.out.println("Please verify the changes and push them to the remote repository:");
        System.out.println("git push origin " + Configuration.BRANCH_DEVELOP.get());
        System.out.println(CONSOLE_SEPARATOR);
    }

    /**
     * Runs the bugfix version update process, this is expected to be run on a release branch.
     * It checks if the current branch is a release branch and if the version does not end with -SNAPSHOT suffix.
     *
     * @param version
     * @param interactive
     */
    private static void runBugfix(String version, boolean interactive) {
        // parse version for branch name
        var currentBranch = getCurrentBranch();
        if (!currentBranch.startsWith("release/")) {
            printWarning("Bugfix version should be updated on release branch!", null);
        }
        if (version.endsWith("-SNAPSHOT")) {
            printWarning("Bugfix version should not end with -SNAPSHOT suffix!", null);
        }
        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("::::::: Performing bugfix version update :::::::");
        System.out.println("\tCurrent branch name : " + currentBranch);
        System.out.println("\tNew bugfix version: " + version);
        System.out.println(CONSOLE_SEPARATOR);
        userConfirm(interactive);

        runMavenVersionUpdate(version);

        System.out.print("Running git... ");
        runCommand(Configuration.COMMAND_GIT.get(), "commit","-am", String.format(Configuration.COMMIT_MESSAGE_BUGFIX.get(), version));
        runCommand(Configuration.COMMAND_GIT.get(), "tag", String.format(Configuration.TAG_RELEASE_PATTERN.get(), version));
        System.out.println("done");

        System.out.println("::::::: Bugfix version update complete :::::::");
        System.out.println("Please verify the changes and push them to the remote repository:");
        System.out.println("git push origin " + currentBranch);
        System.out.println(CONSOLE_SEPARATOR);
    }


    private static void runVersionUpdate(String version, boolean interactive) {
        // parse version for branch name
        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("::::::: Performing version update :::::::");
        System.out.println("\tNew version: " + version);
        System.out.println(CONSOLE_SEPARATOR);
        userConfirm(interactive);

        runMavenVersionUpdate(version);

        System.out.println("::::::: Version update complete :::::::");
        System.out.println(CONSOLE_SEPARATOR);
    }

    public static void main(String[] args) {
        System.out.println(CONSOLE_SEPARATOR);
        System.out.println("MvnRelease script, v" + Configuration.APP_VERSION);
        System.out.println("use --help for full usage information");
        System.out.println(CONSOLE_SEPARATOR);

        // Init..
        System.out.println("Initializing...");
        System.out.print("Checking maven... ");
        int exitCode = runCommand(Configuration.COMMAND_MVN.get(), false, "--version");
        if (exitCode != 0) {
            printError("Maven is not installed or not found in PATH", null, true);
        } else {
            System.out.println("OK");
        }
        System.out.print("Checking git... ");
        exitCode = runCommand(Configuration.COMMAND_GIT.get(), false, "status");
        if (exitCode != 0) {
            printError("Git status failed - it's not installed or the repository doesn't exist", null, true);
        } else {
            System.out.println("OK");
        }
        System.out.println("Working directory: " + new File(Configuration.WORKING_DIR.get()).getAbsolutePath());
        var mavenInfo = parsePom();
        var versionInfo = new VersionInfo(mavenInfo.version);
        System.out.println(mavenInfo);
        System.out.println(CONSOLE_SEPARATOR);

        // ...Init complete
        // Read params...

        Operation operation = null;
        String newVersion = "";
        boolean interactive = false;

        if (args.length == 0) {
            interactive = true;
            operation = askOperation();
            newVersion = askVersion(suggestVersion(operation, versionInfo));
        } else {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--debug")) {
                    debugEnabled = true;
                }
                if (args[i].equals("--confirm")) {
                    interactive = true;
                }
                if (args[i].equals("--help")) {
                    printUsage(versionInfo);
                    System.exit(0);
                }

                for (var op : Operation.values()) {
                    if (args[i].equals(op.toString()) || args[i].equals(op.shortCut)) {
                        if (i + 1 < args.length) {
                            operation = op;
                            newVersion = args[i + 1];
                        } else {
                            printError("Missing version argument for '" + op + "' operation", null, true);
                        }
                    }
                }
            }
        }
        // ...params read

        // do the job
        switch (operation) {
            case RELEASE -> runRelease(newVersion, interactive);
            case DEV     -> runDevelop(newVersion, interactive);
            case BUGFIX  -> runBugfix(newVersion, interactive);
            case VERSION -> runVersionUpdate(newVersion, interactive);
        }
    }

    /**
     * List of possible operations this script can perform.
     */
    private enum Operation {
        RELEASE("release - performs a release and creates a release branch"),
        DEV(    "dev     - create next development version (runs on develop branch only)"),
        BUGFIX( "bugfix  - create bugfix version (should be run on release/ branch), doesn't create a new branch"),
        VERSION("version - replaces the version in pom files and does nothing else");

        private String shortCut;
        private String help;

        Operation(String help) {
            this.shortCut = name().substring(0, 1).toLowerCase();
            this.help = help;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * MavenInfo class is used to parse and store Maven project information.
     */
    private record MavenInfo(String groupId, String artifactId, String version, String name) {
        @Override
        public String toString() {
            return "Project information: " + groupId + ":" + artifactId + ": " + version + " (" + name + ")";
        }
    }

    /**
     * VersionInfo class is used to parse and store version information.
     */
    private static class VersionInfo {
        String major;
        String minor;
        String patch;
        String suffix;
        VersionInfo(String version) {
            // Split the numeric part into major, minor, and patch
            String[] parts = version.split("-");
            String numericPart = parts[0]; // "1.0.0"
            suffix = parts.length > 1 ? parts[1] : ""; // "SNAPSHOT"

            String[] numbers = numericPart.split("\\.");
            major = numbers[0];
            minor = numbers[1];
            if (numbers.length < 3) {
                patch = "0"; // default patch version if not specified
            } else {
                patch = numbers[2];
            }
        }
    }
}
// here be dragons
