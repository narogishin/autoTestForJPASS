package org.example;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;


import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SeleniumPrivilegesCheck {

    public static void main(String[] args) throws IOException {
        // Set up Selenium WebDriver
        WebDriver driver = setupWebDriver();
        driver.get("https://jpass.jesagroup.com/projects/131/jpass");

        try {
            // Call the processPrivileges function with paths to input/output files
            processPrivileges(driver, "src/main/resources/cssSelectors/cssSelector.json", "actuallyPrivilege.json");

            // Call the comparePrivileges function with the paths to your JSON files
            ObjectNode result = comparePrivileges("actuallyPrivilege.json", "src/main/resources/Contractor_HSE_manager.json");

            // Print the result (for demonstration)
            System.out.println(result.toPrettyString());

            // Save the result to a file
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File("result.json"), result);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close the WebDriver
        driver.quit();

        // Call the generateReport function
        generateReport("result.json", "extentReport.html");

        openHtmlFile("extentReport.html");
    }

    private static WebDriver setupWebDriver() {
        EdgeOptions options = new EdgeOptions();
        options.addArguments("user-data-dir=C:\\Users\\hp\\AppData\\Local\\Microsoft\\Edge\\TempUserData");
        options.addArguments("profile-directory=Default");
        WebDriver driver = new EdgeDriver(options);
        driver.manage().window().maximize();
        return driver;
    }

    public static void processPrivileges(WebDriver driver, String jsonFilePath, String outputFilePath) throws IOException {
        // Load JSON file
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(new File(jsonFilePath));

        // Create a map to store the output
        Map<String, ObjectNode> modulePrivilegeMap = new HashMap<>();

        // Iterate through the JSON featurePrivilegeList array
        for (JsonNode featureNode : rootNode.get("featurePrivilegeList")) {
            String featureCode = featureNode.get("featureCode").asText();
            String moduleCode = featureNode.get("moduleCode").asText();
            String cssSelectorR = featureNode.has("cssSelectorR") ? featureNode.get("cssSelectorR").asText() : null;
            String cssSelectorW = featureNode.has("cssSelectorW") ? featureNode.get("cssSelectorW").asText() : null;

            boolean privilegeR = false;
            boolean privilegeW = false;

            // Dynamic handling of clickOn keys
            int clickIndex = 1;
            while (true) {
                // Check visibility
                if (cssSelectorR != null && isElementDisplayed(driver, cssSelectorR)) {
                    privilegeR = true;  // Update to "R" if cssSelectorR is found
                }
                if (cssSelectorW != null && isElementClickable(driver, cssSelectorW)) {
                    privilegeW = true; // Update to "RW" if cssSelectorW is found
                }

                String clickOnKey = "clickOn" + clickIndex;
                if (featureNode.has(clickOnKey)) {
                    String clickSelector = featureNode.get(clickOnKey).asText();
                    clickElement(driver, clickSelector);
                } else {
                    break; // Exit loop when no more clickOn keys are found
                }
                clickIndex++;
            }

            // Determine privilege
            String privilege = "H";  // Default privilege is hidden ("H")
            if (privilegeR) privilege = "R";
            if (privilegeW) privilege = "W";
            if (privilegeR && privilegeW) privilege = "RW";

            // Add the result to the module's ObjectNode
            ObjectNode moduleNode = modulePrivilegeMap.getOrDefault(moduleCode, objectMapper.createObjectNode());
            moduleNode.put(featureCode, privilege);
            modulePrivilegeMap.put(moduleCode, moduleNode);
        }

        // Convert the map to a JSON object and write to a file
        ObjectNode actuallyPrivilegeJson = objectMapper.createObjectNode();
        for (Map.Entry<String, ObjectNode> entry : modulePrivilegeMap.entrySet()) {
            actuallyPrivilegeJson.set(entry.getKey(), entry.getValue());
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFilePath), actuallyPrivilegeJson);
    }

    // Utility method to check if an element is displayed
    private static boolean isElementDisplayed(WebDriver driver, String cssSelector) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));
            WebElement element = driver.findElement(By.cssSelector(cssSelector));
            return element.isDisplayed();
        } catch (Exception e) {
            return false;  // Return false if the element is not found or not displayed
        }
    }

    // Utility method to check if an element is displayed
    private static boolean isElementClickable(WebDriver driver, String cssSelector) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));
            WebElement element = driver.findElement(By.cssSelector(cssSelector));
            return element.isEnabled();
        } catch (Exception e) {
            return false;  // Return false if the element is not found or not displayed
        }
    }

    // Utility method to click on an element
    private static void clickElement(WebDriver driver, String cssSelector) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));
            WebElement element = driver.findElement(By.cssSelector(cssSelector));
            element.click();
        } catch (Exception e) {
            System.out.println("Element not found or not clickable: " + cssSelector);
        }
    }

    // Function to compare privileges between actuallyPrivilege.json and Contractor_HSE_manager.json
    public static ObjectNode comparePrivileges(String actuallyPrivilegeFilePath, String contractorFilePath) throws IOException {
        // Load the two JSON files
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actuallyPrivilegeJson = mapper.readTree(new File(actuallyPrivilegeFilePath));
        JsonNode contractorHseManagerJson = mapper.readTree(new File(contractorFilePath));

        // Create a result object to store the comparison
        ObjectNode resultJson = mapper.createObjectNode();

        // Iterate through each module in Contractor_HSE_manager.json
        for (Iterator<Map.Entry<String, JsonNode>> modules = contractorHseManagerJson.fields(); modules.hasNext();) {
            Map.Entry<String, JsonNode> moduleEntry = modules.next();
            String moduleCode = moduleEntry.getKey();
            JsonNode moduleFeatures = moduleEntry.getValue();

            // Skip non-module fields like "role"
            if (!(moduleFeatures instanceof ObjectNode)) {
                continue;
            }

            ObjectNode moduleResult = mapper.createObjectNode();

            // Iterate through each feature in the Contractor_HSE_manager.json module
            for (Iterator<Map.Entry<String, JsonNode>> features = moduleFeatures.fields(); features.hasNext();) {
                Map.Entry<String, JsonNode> featureEntry = features.next();
                String featureCode = featureEntry.getKey();
                String contractorPrivilege = featureEntry.getValue().asText();

                // Get the privilege from actuallyPrivilege.json if the featureCode exists
                String actuallyPrivilege = getPrivilege(actuallyPrivilegeJson, moduleCode, featureCode);

                // Compare the privileges between the two files
                if (actuallyPrivilege != null) {
                    boolean isMatch = actuallyPrivilege.equals(contractorPrivilege);
                    moduleResult.put(featureCode, isMatch);
                }
                // If the featureCode is not found in actuallyPrivilege.json, do not add it to the result
            }

            // Add the module result to the resultJson only if it contains features
            if (!moduleResult.isEmpty()) {
                resultJson.set(moduleCode, moduleResult);
            }
        }

        return resultJson; // Return the result object
    }

    // Helper method to get privilege from actuallyPrivilege.json
    private static String getPrivilege(JsonNode actuallyPrivilegeJson, String moduleCode, String featureCode) {
        if (actuallyPrivilegeJson.has(moduleCode) && actuallyPrivilegeJson.get(moduleCode).has(featureCode)) {
            return actuallyPrivilegeJson.get(moduleCode).get(featureCode).asText();
        }
        return null; // Return null if not found
    }


    // Function to generate the Extent report
    public static void generateReport(String jsonFilePath, String reportFilePath) {
        // Configure ExtentReports
        ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter(reportFilePath);
        ExtentReports extent = new ExtentReports();
        extent.attachReporter(htmlReporter);

        // Read JSON file
        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = new File(jsonFilePath);
        ObjectNode jsonNode;

        try {
            jsonNode = (ObjectNode) mapper.readTree(jsonFile);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Iterate through JSON data and create report
        Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = jsonNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = fields.next();
            String elementName = field.getKey();
            String privilege = field.getValue().fieldNames().next();
            String status = field.getValue().get(privilege).asText();

            // Create a test for each element
            ExtentTest test = extent.createTest(elementName);
            test.info("Privilege: " + privilege);
            if ("true".equalsIgnoreCase(status)) {
                test.pass("Element is displayed");
            } else {
                test.fail("Element is not displayed");
            }
        }

        // Flush the report
        extent.flush();
        System.out.println("Extent report generated successfully!");
    }

    // Function to open the generated HTML report
    public static void openHtmlFile(String filePath) {
        try {
            File htmlFile = new File(filePath);

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (htmlFile.exists()) {
                    desktop.browse(htmlFile.toURI());  // Opens in the default browser
                } else {
                    System.out.println("File does not exist: " + filePath);
                }
            } else {
                System.out.println("Desktop is not supported on this system.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

