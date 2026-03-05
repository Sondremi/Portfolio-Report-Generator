import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class Konto {
    private static final String PRIMARY_INPUT_FILE = "transactions.csv";
    private static final String FALLBACK_INPUT_FILE = "transactions_example.csv";

    private static final ArrayList<Verdipapir> securities = new ArrayList<>();
    private static final Map<String, Verdipapir> securitiesByKey = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        readFile(resolveInputFile());
        writeToCsv();
    }

    private static String resolveInputFile() {
        if (new File(PRIMARY_INPUT_FILE).exists()) {
            return PRIMARY_INPUT_FILE;
        }
        return FALLBACK_INPUT_FILE;
    }

    private static double parseDoubleOrZero(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        String normalized = value.trim()
                .replace("\u00A0", "")
                .replace("−", "-")
                .replace(" ", "");

        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else {
            normalized = normalized.replace(",", ".");
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static String[] splitCsvLine(String line) {
        return line.replace("−", "-").split(";", -1);
    }

    private static String getCell(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return "";
        }
        return row[index].trim().replace("\"", "");
    }

    private static Verdipapir getOrCreateSecurity(String name, String isin) {
        String key = isin == null || isin.isBlank() ? name : isin;
        Verdipapir security = securitiesByKey.get(key);
        if (security != null) {
            return security;
        }

        Verdipapir newSecurity = new Verdipapir(name, isin);
        securitiesByKey.put(key, newSecurity);
        securities.add(newSecurity);
        return newSecurity;
    }

    private static class HeaderIndexes {
        int securityName;
        int isin;
        int transactionType;
        int amount;
        int quantity;
        int price;
        int result;
        int totalFees;
        int tradeDate;

        HeaderIndexes() {
            securityName = isin = transactionType = amount = quantity = price = result = totalFees = tradeDate = -1;
        }
    }

    private static HeaderIndexes findHeaderIndexes(String headerLine) {
        String[] columns = headerLine.split(";", -1);
        HeaderIndexes indexes = new HeaderIndexes();

        for (int i = 0; i < columns.length; i++) {
            String column = columns[i].trim();
            switch (column) {
                case "Verdipapir" -> indexes.securityName = i;
                case "ISIN" -> indexes.isin = i;
                case "Transaksjonstype" -> indexes.transactionType = i;
                case "Beløp" -> indexes.amount = i;
                case "Antall" -> indexes.quantity = i;
                case "Kurs" -> indexes.price = i;
                case "Resultat" -> indexes.result = i;
                case "Totale Avgifter" -> indexes.totalFees = i;
                case "Handelsdag", "Handelsdato" -> indexes.tradeDate = i;
            }
        }
        return indexes;
    }

    private static void processLine(String line, HeaderIndexes indexes) {
        String[] row = splitCsvLine(line);

        String securityName = getCell(row, indexes.securityName);
        if (securityName.isEmpty()) {
            return;
        }

        String isin = getCell(row, indexes.isin);
        Verdipapir security = getOrCreateSecurity(securityName, isin);
        if (security == null) {
            return;
        }

        processTransaction(security, row, indexes);
    }

    private static void processTransaction(Verdipapir security, String[] row, HeaderIndexes indexes) {
        String transactionType = getCell(row, indexes.transactionType).toUpperCase();
        String tradeDate = getCell(row, indexes.tradeDate);

        double amount = parseDoubleOrZero(getCell(row, indexes.amount));
        double quantity = parseDoubleOrZero(getCell(row, indexes.quantity));
        double price = parseDoubleOrZero(getCell(row, indexes.price));
        double result = parseDoubleOrZero(getCell(row, indexes.result));
        double totalFees = parseDoubleOrZero(getCell(row, indexes.totalFees));

        switch (transactionType) {
            case "SALG", "SELL", "KJØPT", "KJOP", "BUY", "REINVESTERTUTBYTTE" ->
                    security.addTransaction(tradeDate, transactionType, amount, quantity, price, result, totalFees);
            case "UTBYTTE", "DIVIDEND" -> security.addDividend(amount);
            case "INNSKUDD", "UTTAK INTERNET", "PLATTFORMAVGIFT", "TILBAKEBET. FOND AVG",
                    "OVERBELÅNINGSRENTE", "TILBAKEBETALING" -> {
                // Ignored on purpose; these are cash-account events.
            }
            default -> {
                // Intentionally ignored unknown transaction types.
            }
        }
    }

    private static void readFile(String fileName) throws IOException {
        try (Scanner reader = new Scanner(new File(fileName))) {
            if (!reader.hasNextLine()) {
                return;
            }

            String header = reader.nextLine();
            HeaderIndexes indexes = findHeaderIndexes(header);

            while (reader.hasNextLine()) {
                processLine(reader.nextLine(), indexes);
            }
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void writeToCsv() {
        File file = new File("portfolio.csv");
        try (FileWriter writer = new FileWriter(file)) {
            writeOverviewAsCsv(writer);
            writer.write("\n\n");
            writeRealizedSummaryAsCsv(writer);
            writer.write("\n\n");
            writeSaleTradesPerSecurityAsCsv(writer);
            new ProcessBuilder("open", "portfolio.csv").start();
        } catch (IOException e) {
            System.out.println("Failed to write CSV: " + e.getMessage());
        }
    }

    private static void writeOverviewAsCsv(FileWriter writer) throws IOException {
        writer.write("Ticker\tVerdipapir\tAntall\tGAV\tKurs\tKostpris\tMarkedsverdi\tUrealisert Avkastning (%)\tUrealisert Avkastning\tRealisert Avkastning (%)\tRealisert Avkastning\tUtbytte\tAvkastning (%)\tAvkastning\n");        
        int row = 2;
        int startRow = row;

        for (Verdipapir security : securities) {
            String ticker = security.getTicker();
            String name = security.getName();
            
            String tickerCell = ticker.isEmpty() ? name : "=HVISFEIL(AKSJE(\"" + ticker + "\";25);\"-\")";
            String nameCell = ticker.isEmpty() ? name : "=HVISFEIL(AKSJE(A" + row + ";1);\"" + name + "\")";
            
            String marketPriceFormula = "=HVISFEIL(AKSJE(A" + row + ";0);0)";
            
            String costBasisFormula = "VERDI(C" + row + ")*VERDI(D" + row + ")";
            String marketValueFormula = "VERDI(C" + row + ")*VERDI(E" + row + ")";
            
            String unrealizedGainFormula = "=" + marketValueFormula + "-" + costBasisFormula;
            String unrealizedGainPctFormula = "=AVRUND(HVISFEIL(((" + marketValueFormula + ")-(" + costBasisFormula + "))/(" + costBasisFormula + "); 0); 2)";
                        
            String totalGainFormula = "I" + row + "+K" + row + "+L" + row;
            String totalGainPctFormula = "=AVRUND(HVISFEIL((" + totalGainFormula + ")/F" + row + "; 0); 2)";

            writer.write(
                tickerCell + "\t" +
                nameCell + "\t" +
                security.getUnitsOwnedAsText() + "\t" +
                security.getAverageCostAsText() + "\t" +
                marketPriceFormula + "\t" +
                "=" + costBasisFormula + "\t" +
                "=" + marketValueFormula + "\t" +
                unrealizedGainPctFormula + "\t" +
                unrealizedGainFormula + "\t" +
                security.getRealizedReturnPctAsText() + "\t" +
                security.getRealizedGainAsText() + "\t" +
                security.getDividendsAsText() + "\t" +
                totalGainPctFormula + "\t" +
                "=" + totalGainFormula + "\n"
            );

            row++;
        }

        writer.write("\t\t\t\t\t" +
            "=SUMMER(F" + startRow + ":F" + (row-1) + ")\t" +
            "=SUMMER(G" + startRow + ":G" + (row-1) + ")\t" +
            "=SUMMER(H" + startRow + ":H" + (row-1) + ")\t" +
            "=SUMMER(I" + startRow + ":I" + (row-1) + ")\t" +
            "=SUMMER(J" + startRow + ":J" + (row-1) + ")\t" +
            "=SUMMER(K" + startRow + ":K" + (row-1) + ")\t" +
            "=SUMMER(L" + startRow + ":L" + (row-1) + ")\t" +
            "=SUMMER(M" + startRow + ":M" + (row-1) + ")\t" +
            "=SUMMER(N" + startRow + ":N" + (row-1) + ")\n"
        );
    }

    private static void writeRealizedSummaryAsCsv(FileWriter writer) throws IOException {
        writer.write("TOTALOVERSIKT - ALLE SALG\n");
        writer.write("Verdipapir\tSalgssum\tKostnad\tRealisert gevinst/tap\tAvkastning (%)\n");

        ArrayList<Verdipapir> soldSecurities = new ArrayList<>();
        for (Verdipapir security : securities) {
            if (security.hasSales()) {
                soldSecurities.add(security);
            }
        }
        soldSecurities.sort(Comparator.comparing(Verdipapir::getRealizedSalesValue).reversed());

        double totalSalesValue = 0.0;
        double totalCostBasis = 0.0;
        double totalRealizedGain = 0.0;

        for (Verdipapir security : soldSecurities) {
            double salesValue = security.getRealizedSalesValue();
            double costBasis = security.getRealizedCostBasis();
            double gain = security.getRealizedGain();
            double returnPct = costBasis > 0 ? (gain / costBasis) * 100.0 : 0.0;

            totalSalesValue += salesValue;
            totalCostBasis += costBasis;
            totalRealizedGain += gain;

            writer.write(
                security.getName() + "\t" +
                formatNumber(salesValue, 2) + "\t" +
                formatNumber(costBasis, 2) + "\t" +
                formatNumber(gain, 2) + "\t" +
                formatNumber(returnPct, 2) + "\n"
            );
        }

        double totalReturnPct = totalCostBasis > 0 ? (totalRealizedGain / totalCostBasis) * 100.0 : 0.0;
        writer.write(
            "TOTALT\t" +
            formatNumber(totalSalesValue, 2) + "\t" +
            formatNumber(totalCostBasis, 2) + "\t" +
            formatNumber(totalRealizedGain, 2) + "\t" +
            formatNumber(totalReturnPct, 2) + "\n"
        );
    }

    private static void writeSaleTradesPerSecurityAsCsv(FileWriter writer) throws IOException {
        ArrayList<Verdipapir> soldSecurities = new ArrayList<>();
        for (Verdipapir security : securities) {
            if (security.hasSales()) {
                soldSecurities.add(security);
            }
        }
        soldSecurities.sort(Comparator.comparing(Verdipapir::getRealizedSalesValue).reversed());

        for (Verdipapir security : soldSecurities) {
            writer.write("SALGSTRADES - " + security.getName() + "\n");
            writer.write("Salgsdato\tAndeler\tPris/andel\tSalgssum\tKostnad\tGevinst/Tap\tAvkastning (%)\n");

            double totalUnits = 0.0;
            double totalSalesValue = 0.0;
            double totalCostBasis = 0.0;
            double totalGain = 0.0;

            for (Verdipapir.SaleTrade trade : security.getSaleTradesSortedByDate()) {
                totalUnits += trade.getUnits();
                totalSalesValue += trade.getSaleValue();
                totalCostBasis += trade.getCostBasis();
                totalGain += trade.getGainLoss();

                writer.write(
                    trade.getTradeDateAsCsv() + "\t" +
                    formatNumber(trade.getUnits(), 4) + "\t" +
                    formatNumber(trade.getUnitPrice(), 2) + "\t" +
                    formatNumber(trade.getSaleValue(), 2) + "\t" +
                    formatNumber(trade.getCostBasis(), 2) + "\t" +
                    formatNumber(trade.getGainLoss(), 2) + "\t" +
                    formatNumber(trade.getReturnPct(), 2) + "\n"
                );
            }

            double totalReturnPct = totalCostBasis > 0 ? (totalGain / totalCostBasis) * 100.0 : 0.0;
            writer.write(
                "TOTALT\t" +
                formatNumber(totalUnits, 4) + "\t" +
                "\t" +
                formatNumber(totalSalesValue, 2) + "\t" +
                formatNumber(totalCostBasis, 2) + "\t" +
                formatNumber(totalGain, 2) + "\t" +
                formatNumber(totalReturnPct, 2) + "\n\n"
            );
        }
    }

    private static String formatNumber(double value, int decimals) {
        String pattern = "%." + decimals + "f";
        return String.format(Locale.US, pattern, value);
    }
}