package com.amdocs.sanity;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ReadyAPIReportGenerator {

    private ReadyAPIReportGenerator() {
    }

    private static class TestCase {
        String name;
        String status;
        double time;
        String failedStep;

        public TestCase(String name, String status, double time, String failedStep) {
            this.name = name;
            this.status = status;
            this.time = time;
            this.failedStep = failedStep;
        }
    }

    private static class TestSuite {
        String name;
        double time;
        String status;
        List<TestCase> testCases = new ArrayList<>();

        public TestSuite(String name, double time, String status) {
            this.name = name;
            this.time = time;
            this.status = status;
        }
    }

    private static class TestResults {
        List<TestSuite> testSuites = new ArrayList<>();
        int totalTests = 0;
        int totalPassed = 0;
        int totalFailed = 0;
        double totalTime = 0.0;
    }

    private static TestResults parseJUnitXml(File xmlFile, String apiResponsesDir)
            throws IOException, ParserConfigurationException, SAXException {
        TestResults results = new TestResults();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList suiteNodes;

        // Handle both testsuite and testsuites root elements
        if (doc.getDocumentElement().getTagName().equals("testsuites")) {
            suiteNodes = doc.getElementsByTagName("testsuite");
        } else {
            suiteNodes = doc.getChildNodes();
        }

        for (int i = 0; i < suiteNodes.getLength(); i++) {
            if (suiteNodes.item(i) instanceof Element) {
                Element suiteElement = (Element) suiteNodes.item(i);

                if (!suiteElement.getTagName().equals("testsuite")) {
                    continue;
                }

                String nameVal = suiteElement.getAttribute("name");
                String testSuiteName = nameVal.substring(nameVal.indexOf(".") + 1);
                if (testSuiteName.isEmpty())
                    testSuiteName = "Unknown Suite";

                double suiteTime = parseDouble(suiteElement.getAttribute("time"));

                TestSuite testSuite = new TestSuite(testSuiteName, suiteTime, "PASSED");

                NodeList testCaseNodes = suiteElement.getElementsByTagName("testcase");

                for (int j = 0; j < testCaseNodes.getLength(); j++) {
                    Element testCaseElement = (Element) testCaseNodes.item(j);

                    String testCaseName = testCaseElement.getAttribute("name");
                    if (testCaseName.isEmpty())
                        testCaseName = "Unknown Test";

                    double testTime = parseDouble(testCaseElement.getAttribute("time"));

                    String status = "PASSED";
                    String failedStep = "";

                    // Check for failure
                    NodeList failures = testCaseElement.getElementsByTagName("failure");

                    if (failures.getLength() > 0) {
                        status = "FAILED";
                        testSuite.status = "FAILED";

                        String failureText = failures.item(0).getTextContent();

                        int begIdx = failureText.indexOf("<b>") + 3;
                        int endIdx = failureText.indexOf("Failed", begIdx);
                        failedStep = failureText.substring(begIdx, endIdx).trim();

                        // Rename failed step's api_response file
                        if (apiResponsesDir != null && !apiResponsesDir.isEmpty()) {
                            try {
                                renameApiResponseFile(apiResponsesDir, testSuiteName, testCaseName, failedStep);
                            } catch (IOException e) {
                                System.err.println("Failed to rename API response file for failed step: " + failedStep);
                                e.printStackTrace();
                            }
                        }

                        results.totalFailed++;
                    } else {
                        results.totalPassed++;
                    }

                    testSuite.testCases.add(new TestCase(testCaseName, status, testTime, failedStep));
                    results.totalTests++;
                    results.totalTime += testTime;
                }

                results.testSuites.add(testSuite);
            }
        }

        return results;
    }

    private static void renameApiResponseFile(String apiResponsesDir, String testSuiteName, String testCaseName,
            String failedStep) throws IOException {

        failedStep = failedStep.replace("&amp;", "&");

        failedStep = sanitizeFileName(failedStep);
        testSuiteName = sanitizeFileName(testSuiteName);
        testCaseName = sanitizeFileName(testCaseName);

        Path oldPath = Paths.get(apiResponsesDir, testSuiteName, testCaseName, failedStep + ".txt");
        Path newPath = oldPath.resolveSibling(failedStep + " ~FAILED.txt");
        Files.move(oldPath, newPath);
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    private static TestResults mergeResults(List<TestResults> resultsList) {
        TestResults merged = new TestResults();

        for (TestResults results : resultsList) {
            merged.testSuites.addAll(results.testSuites);
            merged.totalTests += results.totalTests;
            merged.totalPassed += results.totalPassed;
            merged.totalFailed += results.totalFailed;
            merged.totalTime += results.totalTime;
        }

        return merged;
    }

    private static void generateHtml(TestResults results, String outputPath, String jobName) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentDate = dateFormat.format(new Date());

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
        html.append("    <title>" + escapeHtml(jobName) + "</title>\n");
        html.append("</head>\n\n");
        html.append("<body style=\"margin:0; padding:0; font-family: Arial, Helvetica, sans-serif;\">\n");
        html.append(
                "    <table width=\"85%\" align=\"center\" cellpadding=\"40\" cellspacing=\"0\" style=\"margin:auto; border:2px solid #242424; border-collapse:collapse;\">\n");
        html.append("        <tr>\n");
        html.append("            <td>\n");
        html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n");
        html.append("                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n");
        html.append("                    <tr>\n");
        html.append("                        <td align=\"center\">\n");
        html.append("                            <h1 style=\"color:#1B3651; margin:0 0 8px 0;\">" + escapeHtml(jobName)
                + "</h1>\n");
        html.append("                        </td>\n");
        html.append("                    </tr>\n");
        html.append("                </table>\n");
        html.append("                <p style=\"text-align:center; margin:0 0 8px 0; font-size:14px;\">\n");
        html.append("                    Generated: " + currentDate + "\n");
        html.append("                </p>\n\n");

        html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n\n");

        html.append("                <h2 style=\"color:#C63; margin:16px 0 8px 0;\">SUMMARY</h2>\n\n");

        html.append("                <table width=\"100%\" cellpadding=\"10\" cellspacing=\"0\">\n");
        html.append("                    <tr valign=\"top\">\n");
        html.append(
                "                        <td width=\"260\" style=\"border:1px solid #dddddd; background-color:#f5f5f5;\">\n");
        html.append("                            <p style=\"margin:0 0 6px 0; font-size:14px;\">\n");
        html.append("                                <strong style=\"color:#1B3651;\">Total Tests:</strong> "
                + results.totalTests + "\n");
        html.append("                            </p>\n");
        html.append("                            <p style=\"margin:0 0 12px 0; font-size:14px;\">\n");
        html.append("                                <strong style=\"color:#1B3651;\">Total Time:</strong> "
                + String.format("%.3f", results.totalTime) + "s\n");
        html.append("                            </p>\n");

        String base64PieChart = Base64PieChart.produce(results.totalPassed, results.totalFailed);

        html.append("                            <div style=\"text-align:center;\">\n");
        html.append("                                <img src=\"data:image/png;base64," + base64PieChart
                + "\" width=\"180\" alt=\"Resultant Pie Chart\" style=\"display:block; margin:auto;\">\n");
        html.append("                            </div>\n");
        html.append("                        </td>\n\n");

        html.append("                        <td width=\"16\">&nbsp;</td>\n\n");

        html.append("                        <td>\n");
        html.append(
                "                            <table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse; border:2px solid #242424; font-size:14px;\">\n");
        html.append("                                <tr>\n");
        html.append(
                "                                    <th width=\"60%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
        html.append("                                        Flow(s)\n");
        html.append("                                    </th>\n");
        html.append(
                "                                    <th width=\"20%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
        html.append("                                        Result\n");
        html.append("                                    </th>\n");
        html.append(
                "                                    <th width=\"20%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
        html.append("                                        Time(s)\n");
        html.append("                                    </th>\n");
        html.append("                                </tr>\n");

        for (TestSuite testSuite : results.testSuites) {
            String statusColor = testSuite.status.equals("PASSED") ? "#008000" : "#FF0000";
            String anchorId = testSuite.name.replaceAll(" ", "_");

            html.append("                                <tr>\n");
            html.append("                                    <td style=\"border:1px solid #0D0000;\">\n");
            html.append("                                        <a href=\"#" + anchorId
                    + "\" style=\"color:#000000; text-decoration:none;\">" + escapeHtml(testSuite.name) + "</a>\n");
            html.append("                                    </td>\n");
            html.append("                                    <td bgcolor=\"" + statusColor
                    + "\" style=\"border:1px solid #0D0000; color:#ffffff;\">\n");
            html.append("                                        " + testSuite.status + "\n");
            html.append("                                    </td>\n");
            html.append(
                    "                                    <td style=\"border:1px solid #0D0000; text-align:right;\">\n");
            html.append("                                        " + String.format("%.3f", testSuite.time) + "\n");
            html.append("                                    </td>\n");
            html.append("                                </tr>\n");
        }

        html.append("                            </table>\n");
        html.append("                        </td>\n");
        html.append("                    </tr>\n");
        html.append("                </table>\n\n");

        html.append("                <br>\n");
        html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n");
        html.append("                <br>\n\n");

        html.append("                <h2 style=\"color:#C63; margin:0 0 8px 0;\">TEST CASES DETAILS</h2>\n");

        for (TestSuite testSuite : results.testSuites) {
            String anchorId = testSuite.name.replaceAll(" ", "_");

            html.append("                <h3 id=\"" + anchorId + "\" style=\"color:#C63; margin:12px 0 8px 0;\">\n");
            html.append("                    " + escapeHtml(testSuite.name) + "\n");
            html.append("                </h3>\n\n");

            html.append(
                    "                <table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse; font-size:14px;\">\n");
            html.append("                    <tr>\n");
            html.append(
                    "                        <th width=\"60%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
            html.append("                            TestCase\n");
            html.append("                        </th>\n");
            html.append(
                    "                        <th width=\"7%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
            html.append("                            Result\n");
            html.append("                        </th>\n");
            html.append(
                    "                        <th width=\"5%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
            html.append("                            Time(s)\n");
            html.append("                        </th>\n");
            if (testSuite.status.equals("FAILED")) {
                html.append(
                        "                        <th width=\"28%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
                html.append("                            Failed Step\n");
                html.append("                        </th>\n");
            }
            html.append("                    </tr>\n");

            for (TestCase testCase : testSuite.testCases) {
                String statusColor = testCase.status.equals("PASSED") ? "#008000" : "#FF0000";

                html.append("                    <tr>\n");
                html.append("                        <td style=\"border:1px solid #0D0000;\">\n");
                html.append("                            " + escapeHtml(testCase.name) + "\n");
                html.append("                        </td>\n");
                html.append("                        <td bgcolor=\"" + statusColor
                        + "\" style=\"border:1px solid #0D0000; color:#ffffff;\">" + testCase.status + "</td>\n");
                html.append("                        <td style=\"border:1px solid #0D0000; text-align:right;\">"
                        + String.format("%.3f", testCase.time) + "</td>\n");
                if (testSuite.status.equals("FAILED")) {
                    html.append("                        <td style=\"border:1px solid #0D0000;\">" + testCase.failedStep
                            + "</td>\n");
                }
                html.append("                    </tr>\n");
            }

            html.append("                </table>\n");
            html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n");
        }

        html.append("            </td>\n");
        html.append("        </tr>\n");
        html.append("    </table>\n");
        html.append("</body>\n\n");

        html.append("</html>\n");

        try (FileWriter writer = new FileWriter(outputPath + File.separator + "summary-report.html")) {
            writer.write(html.toString());
        }
    }

    private static double parseDouble(String value) {
        try {
            return value.isEmpty() ? 0.0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static final class ReportSummary {
        final int totalTests;
        final int totalPassed;
        final int totalFailed;
        final double totalTime;

        ReportSummary(int totalTests, int totalPassed, int totalFailed, double totalTime) {
            this.totalTests = totalTests;
            this.totalPassed = totalPassed;
            this.totalFailed = totalFailed;
            this.totalTime = totalTime;
        }
    }

    static ReportSummary generateReport(String inputPath, String outputPath, String jobName, String apiResponsesDir,
            Consumer<String> logger) throws IOException, ParserConfigurationException, SAXException {

        if (logger == null) {
            logger = s -> {
            };
        }

        List<File> xmlFiles = new ArrayList<>();
        File input = new File(inputPath);

        if (!input.isDirectory()) {
            throw new IllegalArgumentException("Input path is not a directory: " + inputPath);
        }

        // Process all XML files in directory
        try (Stream<Path> paths = Files.walk(Paths.get(inputPath))) {
            xmlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .filter(p -> p.getFileName().toString().startsWith("TEST-"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }

        if (xmlFiles.isEmpty()) {
            throw new IllegalArgumentException("No XML files found in input path: " + inputPath);
        }

        logger.accept("Found " + xmlFiles.size() + " XML file(s)");

        // Parse all XML files
        List<TestResults> allResults = new ArrayList<>();
        for (File xmlFile : xmlFiles) {
            logger.accept("Parsing: " + xmlFile.getPath());
            TestResults results = parseJUnitXml(xmlFile, apiResponsesDir);
            allResults.add(results);
        }

        // Merge results if multiple files
        TestResults finalResults;
        if (allResults.size() > 1) {
            logger.accept("Merging results from multiple files...");
            finalResults = mergeResults(allResults);
        } else {
            finalResults = allResults.get(0);
        }

        String htmlReportPath = outputPath + File.separator + "summary-report.html";
        logger.accept("Generating HTML report: " + htmlReportPath);
        generateHtml(finalResults, outputPath, jobName);
        logger.accept("HTML report generated: " + htmlReportPath);

        return new ReportSummary(
                finalResults.totalTests,
                finalResults.totalPassed,
                finalResults.totalFailed,
                finalResults.totalTime);
    }
}