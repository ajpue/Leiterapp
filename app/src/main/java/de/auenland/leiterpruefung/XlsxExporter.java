package de.auenland.leiterpruefung;

import android.database.Cursor;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private XlsxExporter() {}

    public static void write(Cursor c, File out) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>");

            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>");

            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"Prüfungen\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");

            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>");

            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            StringBuilder sheet = new StringBuilder();
            sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");

            String[] headers = {
                    "Leiter", "Prüfer", "Datum/Zeit", "Standort", "Leiterart",
                    "Sprossen/Stufen", "Intervall Monate", "Nächste Prüfung", "Status", "Mangel",
                    "Foto", "1 Holme/Sprossen", "2 Verbindungen", "3 Füße/Gelenke",
                    "4 Verriegelungen", "5 Kennzeichnung", "6 Gesamtprüfung"
            };
            appendRow(sheet, 1, headers);

            SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);
            int rowNum = 2;
            while (c.moveToNext()) {
                String[] row = {
                        get(c, "ladder_id"),
                        get(c, "inspector"),
                        df.format(new Date(c.getLong(c.getColumnIndexOrThrow("ts")))),
                        get(c, "location"),
                        get(c, "ladder_type"),
                        get(c, "rung_count"),
                        String.valueOf(c.getInt(c.getColumnIndexOrThrow("interval_months"))),
                        df.format(new Date(c.getLong(c.getColumnIndexOrThrow("next_ts")))),
                        get(c, "status"),
                        get(c, "defect"),
                        get(c, "photo_path"),
                        get(c, "s1"), get(c, "s2"), get(c, "s3"),
                        get(c, "s4"), get(c, "s5"), get(c, "s6")
                };
                appendRow(sheet, rowNum++, row);
            }
            sheet.append("</sheetData></worksheet>");
            zip.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } finally {
            c.close();
        }
    }

    private static String get(Cursor c, String col) {
        int idx = c.getColumnIndexOrThrow(col);
        return c.isNull(idx) ? "" : c.getString(idx);
    }

    private static void appendRow(StringBuilder sb, int rowNum, String[] values) {
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int i = 0; i < values.length; i++) {
            String ref = columnName(i + 1) + rowNum;
            sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
              .append(xml(values[i]))
              .append("</t></is></c>");
        }
        sb.append("</row>");
    }

    private static String columnName(int n) {
        StringBuilder s = new StringBuilder();
        while (n > 0) {
            int r = (n - 1) % 26;
            s.insert(0, (char)('A' + r));
            n = (n - 1) / 26;
        }
        return s.toString();
    }

    private static String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void put(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
