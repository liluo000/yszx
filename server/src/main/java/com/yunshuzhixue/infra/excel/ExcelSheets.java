package com.yunshuzhixue.infra.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Excel 读写（Apache POI）。批量导入单次不超过 1000 行（04.API接口设计.md 2.9） */
public final class ExcelSheets {

    public static final int MAX_IMPORT_ROWS = 1000;

    /** 一行数据：rowNo 为 Excel 中的行号（从 1 开始），cells 为各列文本 */
    public record SheetRow(int rowNo, List<String> cells) {
        public String cell(int index) {
            return index < cells.size() ? cells.get(index) : "";
        }
    }

    public static class TooManyRowsException extends RuntimeException {
        public TooManyRowsException() {
            super("超过 " + MAX_IMPORT_ROWS + " 行");
        }
    }

    private ExcelSheets() {
    }

    /** 带表头的表格：headers 为第 1 行各列文本，rows 为之后的非空行（列数与表头一致） */
    public record Table(List<String> headers, List<SheetRow> rows) {
    }

    /** 读取第一个工作表：第 1 行为表头，按表头列数读取数据行，跳过空行 */
    public static Table readTable(InputStream in) throws IOException {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = new ArrayList<>();
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    headers.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
            }
            List<SheetRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (headerRow == null || row.getRowNum() <= headerRow.getRowNum()) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                boolean blank = true;
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    String text = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    blank &= text.isEmpty();
                    cells.add(text);
                }
                if (blank) {
                    continue;
                }
                if (rows.size() >= MAX_IMPORT_ROWS) {
                    throw new TooManyRowsException();
                }
                rows.add(new SheetRow(row.getRowNum() + 1, cells));
            }
            return new Table(headers, rows);
        }
    }

    /** 读取第一个工作表的数据行，跳过第 1 行表头和空行 */
    public static List<SheetRow> readRows(InputStream in, int columns) throws IOException {
        DataFormatter formatter = new DataFormatter();
        List<SheetRow> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                boolean blank = true;
                for (int i = 0; i < columns; i++) {
                    Cell cell = row.getCell(i);
                    String text = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    blank &= text.isEmpty();
                    cells.add(text);
                }
                if (blank) {
                    continue;
                }
                if (rows.size() >= MAX_IMPORT_ROWS) {
                    throw new TooManyRowsException();
                }
                rows.add(new SheetRow(row.getRowNum() + 1, cells));
            }
        }
        return rows;
    }

    /** 生成只有表头的导入模板 */
    public static byte[] template(String sheetName, List<String> headers) {
        return write(sheetName, headers, List.of());
    }

    /** 一个工作表的内容 */
    public record SheetData(String name, List<String> headers, List<List<String>> rows) {
    }

    /** 生成包含多个工作表的导出文件 */
    public static byte[] writeSheets(List<SheetData> sheets) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (SheetData data : sheets) {
                Sheet sheet = workbook.createSheet(data.name());
                Row header = sheet.createRow(0);
                for (int i = 0; i < data.headers().size(); i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(data.headers().get(i));
                    cell.setCellStyle(style);
                    sheet.setColumnWidth(i, 22 * 256);
                }
                for (int r = 0; r < data.rows().size(); r++) {
                    Row row = sheet.createRow(r + 1);
                    List<String> values = data.rows().get(r);
                    for (int i = 0; i < values.size(); i++) {
                        row.createCell(i).setCellValue(values.get(i) == null ? "" : values.get(i));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }

    /** 生成导出文件：第 1 行为表头，之后每行为一条数据 */
    public static byte[] write(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 30 * 256);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int i = 0; i < values.size(); i++) {
                    row.createCell(i).setCellValue(values.get(i) == null ? "" : values.get(i));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败", e);
        }
    }
}
