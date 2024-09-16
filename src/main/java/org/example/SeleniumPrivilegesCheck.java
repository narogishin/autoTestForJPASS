package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SeleniumPrivilegesCheck {

    public static void main(String[] args) throws IOException {
        // Set up Selenium WebDriver
        EdgeOptions options = new EdgeOptions();
        options.addArguments("user-data-dir=C:\\Users\\hp\\AppData\\Local\\Microsoft\\Edge\\TempUserData");
        options.addArguments("profile-directory=Default");
        WebDriver driver = new EdgeDriver(options);
        driver.manage().window().maximize();
        driver.get("https://jpass.jesagroup.com/projects/131/jpass");

        // Load JSON file
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(new File("C:\\Users\\hp\\Downloads\\autotest\\src\\main\\resources\\cssSelectors\\cssSelector.json"));

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
                if (cssSelectorW != null && isElementDisplayed(driver, cssSelectorW)) {
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

            String privilege = "H";
            if(privilegeR) privilege = "R";
            if(privilegeW) privilege = "W";
            if(privilegeR && privilegeW ) privilege = "RW";
            // Add the result to the module's ObjectNode
            ObjectNode moduleNode = modulePrivilegeMap.getOrDefault(moduleCode, objectMapper.createObjectNode());
            moduleNode.put(featureCode, privilege);
            modulePrivilegeMap.put(moduleCode, moduleNode);
        }

        // Convert the map to a JSON object and write to a file
        ObjectNode outputJson = objectMapper.createObjectNode();
        for (Map.Entry<String, ObjectNode> entry : modulePrivilegeMap.entrySet()) {
            outputJson.set(entry.getKey(), entry.getValue());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File("output.json"), outputJson);

        try {
            // Call the comparePrivileges function with the paths to your JSON files
            ObjectNode result = comparePrivileges("output.json", "src/main/resources/Contractor_HSE_manager.json");

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

    // Function to compare privileges between output.json and Contractor_HSE_manager.json
    public static ObjectNode comparePrivileges(String outputFilePath, String contractorFilePath) throws IOException {
        // Load the two JSON files
        ObjectMapper mapper = new ObjectMapper();
        JsonNode outputJson = mapper.readTree(new File(outputFilePath));
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

                // Get the privilege from output.json if the featureCode exists
                String outputPrivilege = getPrivilege(outputJson, moduleCode, featureCode);

                // Compare the privileges between the two files
                if (outputPrivilege != null) {
                    boolean isMatch = outputPrivilege.equals(contractorPrivilege);
                    moduleResult.put(featureCode, isMatch);
                }
                // If the featureCode is not found in output.json, do not add it to the result
            }

            // Add the module result to the resultJson only if it contains features
            if (!moduleResult.isEmpty()) {
                resultJson.set(moduleCode, moduleResult);
            }
        }

        return resultJson; // Return the result object
    }

    // Helper method to get privilege from output.json
    private static String getPrivilege(JsonNode outputJson, String moduleCode, String featureCode) {
        if (outputJson.has(moduleCode) && outputJson.get(moduleCode).has(featureCode)) {
            return outputJson.get(moduleCode).get(featureCode).asText();
        }
        return null; // Return null if not found
    }
}

