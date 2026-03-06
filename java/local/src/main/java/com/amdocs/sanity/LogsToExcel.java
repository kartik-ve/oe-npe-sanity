package com.amdocs.sanity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class LogsToExcel {

    private LogsToExcel() {
    }

    static void log(Path logFile, Path excelPath, String flow, int project, String dmp, String env, String tester) throws IOException {
        List<String> exceptions;

        Flow f;
        try {
            f = Flow.valueOf(flow.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid flow: " + flow, e);
        }

        try (BufferedReader br = new BufferedReader(new FileReader(logFile.toFile()), 32 * 1024);) {
            exceptions = findErrors(br);
            createExcel(exceptions, excelPath, f, project, dmp, env, tester);
        }
    }

    private static List<String> findErrors(BufferedReader br) throws IOException {
        List<String> exceptions = new ArrayList<>();

        int openTags = 0;
        String line;
        StringBuilder exception = new StringBuilder();
        while ((line = br.readLine()) != null) {
            int len = line.trim().length();
            if (len == 0 && openTags == 0) {
                continue;
            }

            // Check if session-id encountered at EOF
            if (len >= 13 && len <= 15) {
                boolean isDigitOnly = true;
                for (char c : line.trim().toCharArray()) {
                    if (!Character.isDigit(c)) {
                        isDigitOnly = false;
                        break;
                    }
                }

                if (!isDigitOnly) {
                    break;
                }
            }

            openTags += calculateOpenTags(line);
            if (openTags == 0) {
                exceptions.add(exception.toString().trim());
                exception.setLength(0);
            } else {
                exception.append(line);
            }
        }

        return exceptions;
    }

    private static int calculateOpenTags(String line) {
        int open = 0;
        for (char c : line.toCharArray()) {
            if (c == '<') {
                open++;
            } else if (c == '>') {
                open--;
            }
        }

        return open;
    }

    private static enum Flow {
        NC("NC"),
        COS("COS"),
        CR("CR"),
        RP("RP"),
        MT("MT"),
        BT("BT"),
        SU("SU");

        private final String label;

        Flow(String label) {
            this.label = label;
        }

        String sheetName(boolean isOE) {
            return label + (isOE ? " - OE" : " - CO");
        }
    }

    private static void createExcel(List<String> exceptions, Path excelPath,
            Flow flow, int project, String dmp, String env, String tester) throws IOException {

        File file = excelPath.toFile();
        Workbook workbook;

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                workbook = WorkbookFactory.create(fis);
            }
        } else {
            workbook = new XSSFWorkbook();
        }

        String sheetName = flow.sheetName(project == 1);

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet != null) {
            workbook.removeSheetAt(workbook.getSheetIndex(sheet));
        }
        sheet = workbook.createSheet(sheetName);

        /* ================= HEADER STYLE ================= */

        XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
        headerStyle.setFillForegroundColor(
                new XSSFColor(new java.awt.Color(233, 113, 50), null)); // #E97132
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        /* ================= CENTER STYLE (Non-exception columns) ================= */

        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        /* ================= WRAP STYLE (Exception column) ================= */

        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

        /* ================= CREATE HEADER ================= */

        Row header = sheet.createRow(0);

        String[] headers = { "S.No.", "Exception", "DMP", "ENV", "Tester" };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        /* ================= DATA ROWS ================= */

        int rowNum = 1;
        for (String exception : exceptions) {
            Row row = sheet.createRow(rowNum);

            Cell c0 = row.createCell(0);
            c0.setCellValue(rowNum);
            c0.setCellStyle(centerStyle);

            Cell exceptionCell = row.createCell(1);
            exceptionCell.setCellValue(exception.trim());
            exceptionCell.setCellStyle(wrapStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(dmp);
            c2.setCellStyle(centerStyle);

            Cell c3 = row.createCell(3);
            c3.setCellValue(env);
            c3.setCellStyle(centerStyle);

            Cell c4 = row.createCell(4);
            c4.setCellValue(tester);
            c4.setCellStyle(centerStyle);

            row.setHeightInPoints(90);

            rowNum++;
        }

        /* ================= COLUMN WIDTH ================= */

        sheet.setColumnWidth(1, 100 * 256);

        for (int i = 0; i < 5; i++) {
            if (i != 1) {
                sheet.autoSizeColumn(i);
            }
        }

        /* ================= WRITE FILE ================= */

        try (Workbook wb = workbook;
                FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
        }
    }
}