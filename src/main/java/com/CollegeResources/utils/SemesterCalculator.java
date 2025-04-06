package com.CollegeResources.utils;

import java.time.LocalDate;
import java.time.Month;

public class SemesterCalculator {

    /**
     * Calculates the current semester number for a student based on their batch year
     * and the current date.
     *
     * @param batchYear Two-digit year (e.g., "23" for 2023)
     * @return The current semester number (1-8) or 0 if not in an active semester
     */
    public static int calculateCurrentSemester(String batchYear) {
        if (batchYear == null || batchYear.length() != 2) {
            return 0;
        }

        try {
            // Convert batch year to full year (e.g., "23" -> 2023)
            int fullBatchYear = 2000 + Integer.parseInt(batchYear);

            // Get current date
            LocalDate now = LocalDate.now();
            int currentYear = now.getYear();
            Month currentMonth = now.getMonth();

            // Calculate years passed since batch started
            int yearsPassed = currentYear - fullBatchYear;

            // Determine semester based on month and years passed
            if (yearsPassed < 0) {
                // Future batch, not started yet
                return 0;
            } else if (yearsPassed > 4) {
                // Graduated (completed 8 semesters)
                return 9; // Indicating graduated status
            }

            // Check if we're in an active semester period
            boolean isAugustToDecember = currentMonth.getValue() >= Month.AUGUST.getValue() &&
                    currentMonth.getValue() <= Month.DECEMBER.getValue();
            boolean isJanuaryToMay = currentMonth.getValue() >= Month.JANUARY.getValue() &&
                    currentMonth.getValue() <= Month.MAY.getValue();

            if (!isAugustToDecember && !isJanuaryToMay) {
                // June-July is break period
                // Return the last completed semester
                if (yearsPassed == 0) {
                    return isJanuaryToMay ? 2 : 1;
                } else {
                    return yearsPassed * 2 + (isJanuaryToMay ? 0 : -1);
                }
            }

            // Calculate semester number
            if (yearsPassed == 0) {
                // First year
                return isAugustToDecember ? 1 : 2;
            } else {
                // Second year onwards
                int baseSemester = yearsPassed * 2;
                return isAugustToDecember ? baseSemester + 1 : baseSemester;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets the semester name (e.g., "August 2023 - December 2023") based on batch year and semester number
     *
     * @param batchYear Two-digit year (e.g., "23" for 2023)
     * @param semesterNumber The semester number (1-8)
     * @return String representation of semester date range
     */
    public static String getSemesterDateRange(String batchYear, int semesterNumber) {
        if (batchYear == null || batchYear.length() != 2 || semesterNumber < 1 || semesterNumber > 8) {
            return "Invalid semester";
        }

        try {
            int fullBatchYear = 2000 + Integer.parseInt(batchYear);

            // Calculate year offset based on semester number
            int yearOffset = (semesterNumber - 1) / 2;
            boolean isOddSemester = semesterNumber % 2 == 1;

            LocalDate now = LocalDate.now();
            int currentYear = now.getYear();


            if (isOddSemester) {
                // August-December semester
                return "August " + currentYear + " - December " + currentYear;
            } else {
                // January-May semester
                return "January " + currentYear + " - May " + currentYear;
            }
        } catch (NumberFormatException e) {
            return "Invalid semester";
        }
    }
}