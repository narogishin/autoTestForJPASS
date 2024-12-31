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

import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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


    // TODO : Optimize the following method by reducing the nests
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
            String cssSelectorD = featureNode.has("cssSelectorD") ? featureNode.get("cssSelectorD").asText() : null;

            String xpathR = featureNode.has("xpathR") ? featureNode.get("xpathR").asText() : null;
            String xpathW = featureNode.has("xpathW") ? featureNode.get("xpathW").asText() : null;
            String xpathD = featureNode.has("xpathD") ? featureNode.get("xpathD").asText() : null;

            // Dynamic handling of clickOn keys
            int clickOnIndex = 1;
            int clickOffIndex = 1;

            while (true) {
                // Check visibility
                String clickOnKey = "clickOn" + clickOnIndex;
                if (featureNode.has(clickOnKey)) {
                    String clickSelector = featureNode.get(clickOnKey).asText();
                    clickElement(driver, clickSelector); // TEMPORARY: to check is selectors won't cause issues on clicking elements
                } else {
                    break; // Exit loop when no more clickOn keys are found
                }
                clickOnIndex++;
            }

            boolean privilegeR = (cssSelectorR != null || xpathR != null) && isElementDisplayed(driver, cssSelectorR, xpathR);
            boolean privilegeW = (cssSelectorW != null || xpathW != null) && isElementClickable(driver, cssSelectorW, xpathW);
            boolean privilegeD = (cssSelectorD != null || xpathD != null) && isElementClickable(driver, cssSelectorD, xpathD);

            while (true) {

                String clickOffKey = "clickOff" + clickOffIndex;
                if (featureNode.has(clickOffKey)) {
                    String clickSelector = featureNode.get(clickOffKey).asText();
                    clickElement(driver, clickSelector); // TEMPORARY: to check is selectors won't cause issues on clicking elements
                } else {
                    break; // Exit loop when no more clickOn keys are found
                }
                clickOffIndex++;
            }

            // Determine privilege
            String privilege = "H";  // Default privilege is hidden ("H")
            if (privilegeR) privilege = "R";
//            if (privilegeW) privilege = "W";
//            if (privilegeD) privilege = "D";
            if (privilegeR && privilegeW) privilege = "RW";
            if (privilegeR && privilegeW && privilegeD) privilege = "RWD";

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
    private static boolean isElementDisplayed(WebDriver driver, String cssSelector, String xpath) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            WebDriverWait wait = new WebDriverWait(driver,Duration.ofSeconds(5));
            WebElement element = driver.findElement(cssSelector != null ? By.cssSelector(cssSelector) : By.xpath(xpath));
            wait.until(ExpectedConditions.visibilityOf(element));
            return element.isDisplayed();
        } catch (Exception e) {
            return false;  // Return false if the element is not found or not displayed
        }
    }

//     Utility method to check if an element is displayed
    private static boolean isElementClickable(WebDriver driver, String cssSelector, String xpath) {
        try {
            WebDriverWait wait = new WebDriverWait(driver,Duration.ofSeconds(5));
//            wait.until(ExpectedConditions.visibilityOf(element));
            wait.until(ExpectedConditions.elementToBeClickable(cssSelector != null ? By.cssSelector(cssSelector) : By.xpath(xpath)));
            driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
            WebElement element = driver.findElement(cssSelector != null ? By.cssSelector(cssSelector) : By.xpath(xpath));
            return element.isEnabled();
        } catch (Exception e) {
            return false;  // Return false if the element is not found or not displayed
        }
    }

    // Utility method to click on an element
    private static void clickElement(WebDriver driver, String cssSelector) {
        try {
            WebDriverWait wait = new WebDriverWait(driver,Duration.ofSeconds(5));
            System.out.println(cssSelector.contains(">"));
            wait.until(ExpectedConditions.elementToBeClickable(cssSelector.contains(">") ? By.cssSelector(cssSelector) : By.xpath(cssSelector)));
            driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
            WebElement element = driver.findElement(cssSelector.contains(">") ? By.cssSelector(cssSelector) : By.xpath(cssSelector));
            element.click();
        } catch (Exception e) {
            System.out.println("Element not found or not clickable: " + cssSelector);
//            System.out.println(e.toString());
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
