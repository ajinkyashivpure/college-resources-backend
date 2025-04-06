package com.CollegeResources.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailValidator {

    // Pattern to match PEC email format: name.bt23cseds@pec.edu.in
    private static final Pattern PEC_EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9.]+\\.(bt\\d{2}[a-zA-Z]+)@pec\\.edu\\.in$");

    /**
     * Validates if the email is a valid PEC email address
     * @param email The email to validate
     * @return true if the email is a valid PEC address, false otherwise
     */
    public static boolean isPecEmail(String email) {
        if (email == null) return false;
        return email.endsWith("@pec.edu.in");
    }

    /**
     * Extracts the batch year and department from the email
     * @param email The PEC email address
     * @return A string containing the batch and department (e.g., "bt23cseds")
     */
    public static String extractBatchAndDepartment(String email) {
        if (!isPecEmail(email)) return null;

        Matcher matcher = PEC_EMAIL_PATTERN.matcher(email);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts just the batch year (e.g., "23" from "bt23cseds")
     */
    public static String extractBatchYear(String email) {
        String batchDept = extractBatchAndDepartment(email);
        if (batchDept != null && batchDept.startsWith("bt") && batchDept.length() >= 4) {
            return batchDept.substring(2, 4);
        }
        return null;
    }

    /**
     * Extracts just the department (e.g., "cseds" from "bt23cseds")
     */
    public static String extractDepartment(String email) {
        String batchDept = extractBatchAndDepartment(email);
        if (batchDept != null && batchDept.startsWith("bt") && batchDept.length() >= 5) {
            return batchDept.substring(4);
        }
        return null;
    }
}