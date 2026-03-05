import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Verdipapir {
    private static final DateTimeFormatter CSV_DATE_OUTPUT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final double EPSILON = 0.0000001;

    private final String name;
    private final String isin;
    private String ticker = "";

    private final ArrayDeque<BuyLot> buyLots = new ArrayDeque<>();
    private final ArrayList<SaleTrade> saleTrades = new ArrayList<>();

    private double unitsOwned = 0.0;
    private double dividends = 0.0;
    private double realizedGain = 0.0;
    private double realizedCostBasis = 0.0;
    private double realizedSalesValue = 0.0;

    private static class BuyLot {
        LocalDate tradeDate;
        double remainingUnits;
        double unitCost;

        BuyLot(LocalDate tradeDate, double remainingUnits, double unitCost) {
            this.tradeDate = tradeDate;
            this.remainingUnits = remainingUnits;
            this.unitCost = unitCost;
        }
    }

    public static class SaleTrade {
        private final LocalDate tradeDate;
        private final double units;
        private final double unitPrice;
        private final double saleValue;
        private final double costBasis;
        private final double gainLoss;
        private final double returnPct;

        SaleTrade(LocalDate tradeDate, double units, double unitPrice, double saleValue,
                  double costBasis, double gainLoss, double returnPct) {
            this.tradeDate = tradeDate;
            this.units = units;
            this.unitPrice = unitPrice;
            this.saleValue = saleValue;
            this.costBasis = costBasis;
            this.gainLoss = gainLoss;
            this.returnPct = returnPct;
        }

        public LocalDate getTradeDate() { return tradeDate; }
        public String getTradeDateAsCsv() { return tradeDate.equals(LocalDate.MIN) ? "" : tradeDate.format(CSV_DATE_OUTPUT); }
        public double getUnits() { return units; }
        public double getUnitPrice() { return unitPrice; }
        public double getSaleValue() { return saleValue; }
        public double getCostBasis() { return costBasis; }
        public double getGainLoss() { return gainLoss; }
        public double getReturnPct() { return returnPct; }
    }

    public Verdipapir(String n, String i) {
        name = n;
        isin = i;
        setTicker();
    }

    public String getName() { return name; }
    public String getTicker() { return ticker; }
    public String getIsin() { return isin; }

    public String getAverageCostAsText() { return String.format("%.2f", getAverageCost()); }
    public String getUnitsOwnedAsText() { return String.format("%.2f", unitsOwned); }

    public String getDividendsAsText() { return String.format("%.2f", dividends); }

    public String getRealizedGainAsText() { return String.format("%.2f", realizedGain); }

    public String getRealizedReturnPctAsText() {
        if (Math.abs(realizedCostBasis) < EPSILON) {
            return "0.00";
        }
        double percent = (realizedGain / realizedCostBasis) * 100.0;
        return String.format("%.2f", percent);
    }

    public double getRealizedSalesValue() { return realizedSalesValue; }
    public double getRealizedCostBasis() { return realizedCostBasis; }
    public double getRealizedGain() { return realizedGain; }

    public boolean hasSales() {
        return !saleTrades.isEmpty();
    }

    public ArrayList<SaleTrade> getSaleTradesSortedByDate() {
        ArrayList<SaleTrade> sorted = new ArrayList<>(saleTrades);
        sorted.sort(Comparator.comparing(SaleTrade::getTradeDate));
        return sorted;
    }

    public double getTotalSoldUnits() {
        return saleTrades.stream().mapToDouble(SaleTrade::getUnits).sum();
    }

    public double getAverageCost() {
        if (Math.abs(unitsOwned) < EPSILON) {
            return 0.0;
        }

        double remainingCost = 0.0;
        for (BuyLot lot : buyLots) {
            remainingCost += lot.remainingUnits * lot.unitCost;
        }
        return remainingCost / unitsOwned;
    }

    public void addDividend(double amount) {
        dividends += amount;
    }

    public void addTransaction(String tradeDateText, String transactionType, double amount,
                               double quantity, double price, double reportedResult,
                               double totalFees) {
        double units = Math.abs(quantity);
        if (units < EPSILON) {
            return;
        }

        boolean isBuy = isBuyTransaction(transactionType, amount);
        LocalDate tradeDate = parseDate(tradeDateText);

        if (isBuy) {
            registerBuy(tradeDate, units, amount, price, totalFees);
        } else {
            registerSale(tradeDate, units, amount, price, totalFees);
        }
    }

    private boolean isBuyTransaction(String transactionType, double amount) {
        String normalized = transactionType == null ? "" : transactionType.toUpperCase();
        if (normalized.contains("KJØP") || normalized.contains("KJOP") || normalized.contains("BUY")
                || normalized.contains("REINVEST")) {
            return true;
        }
        if (normalized.contains("SALG") || normalized.contains("SELL")) {
            return false;
        }
        return amount < 0;
    }

    private void registerBuy(LocalDate tradeDate, double units, double amount, double price, double totalFees) {
        double cashOut = Math.abs(amount);
        if (cashOut < EPSILON && price > 0) {
            cashOut = units * price + Math.max(totalFees, 0.0);
        }

        double unitCost = cashOut / units;
        buyLots.addLast(new BuyLot(tradeDate, units, unitCost));
        unitsOwned += units;
    }

    private void registerSale(LocalDate tradeDate, double units, double amount, double price, double totalFees) {
        double saleValue = Math.abs(amount);
        if (saleValue < EPSILON && price > 0) {
            saleValue = units * price - Math.max(totalFees, 0.0);
        }

        double costBasis = consumeLotsUsingFifo(units);
        double gainLoss = saleValue - costBasis;
        double returnPct = costBasis > EPSILON ? (gainLoss / costBasis) * 100.0 : 0.0;

        saleTrades.add(new SaleTrade(tradeDate, units, price, saleValue, costBasis, gainLoss, returnPct));

        unitsOwned -= units;
        if (unitsOwned < EPSILON) {
            unitsOwned = 0.0;
        }

        realizedSalesValue += saleValue;
        realizedCostBasis += costBasis;
        realizedGain += gainLoss;
    }

    private double consumeLotsUsingFifo(double unitsToSell) {
        double remainingUnitsToSell = unitsToSell;
        double costBasis = 0.0;

        while (remainingUnitsToSell > EPSILON && !buyLots.isEmpty()) {
            BuyLot oldestBuy = buyLots.peekFirst();
            if (oldestBuy == null) {
                break;
            }

            double unitsFromLot = Math.min(remainingUnitsToSell, oldestBuy.remainingUnits);
            costBasis += unitsFromLot * oldestBuy.unitCost;
            oldestBuy.remainingUnits -= unitsFromLot;
            remainingUnitsToSell -= unitsFromLot;

            if (oldestBuy.remainingUnits <= EPSILON) {
                buyLots.removeFirst();
            }
        }

        return costBasis;
    }

    private LocalDate parseDate(String tradeDateText) {
        if (tradeDateText == null || tradeDateText.isBlank()) {
            return LocalDate.MIN;
        }

        String value = tradeDateText.trim();
        DateTimeFormatter[] formats = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
        };

        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next known format.
            }
        }
        return LocalDate.MIN;
    }

    private void setTicker() {
        if (isin == null || isin.isEmpty()) {
            ticker = "";
            return;
        }

        try {
            String url = "https://query2.finance.yahoo.com/v1/finance/search?q=" +
                         URLEncoder.encode(isin, "UTF-8") + "&quotesCount=1";

            String response = httpGetRequest(url);

            String symbol = extractValue(response, "symbol");
            String exchange = extractValue(response, "exchange");
            String quoteType = extractValue(response, "quoteType");

            if (symbol != null && !symbol.isEmpty()) {
                if ("ETF".equals(quoteType) || "MUTUALFUND".equals(quoteType)) {
                    ticker = symbol;
                } else if (exchange != null && !exchange.isEmpty()) {
                    String exchangeSuffix = getExchangeSuffix(exchange);
                    ticker = symbol + (exchangeSuffix.isEmpty() ? "" : "." + exchangeSuffix);
                } else {
                    ticker = symbol;
                }
            } else {
                ticker = "";
            }
        } catch (Exception e) {
            System.err.println("Yahoo Finance ISIN lookup failed: " + e.getMessage());
            ticker = "";
        }
    }

    private String getExchangeSuffix(String exchangeName) {
        return switch (exchangeName.toLowerCase()) {
            case "oslo" -> "OL";
            case "new york stock exchange", "nyse" -> "NYSE";
            case "nasdaq" -> "";
            case "london" -> "L";
            case "xetra", "frankfurt" -> "DE";
            case "paris" -> "PA";
            case "tokyo" -> "T";
            case "hong kong" -> "HK";
            case "sydney" -> "AX";
            case "toronto" -> "TO";
            default -> "";
        };
    }

    private String extractValue(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + key + "\\\":\\\"?(.*?)(\\\"|,|})");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            return value.equals("null") ? null : value;
        }
        return null;
    }

    private String httpGetRequest(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }


    // Backward-compatible wrappers used by existing code.
    public String hentNavn() { return getName(); }
    public String hentTicker() { return getTicker(); }
    public String hentGav() { return getAverageCostAsText(); }
    public String hentAntall() { return getUnitsOwnedAsText(); }
    public String hentUtbytte() { return getDividendsAsText(); }
    public String hentRealisertAvkastning() { return getRealizedGainAsText(); }
    public String hentRealisertAvkastningProsent() { return getRealizedReturnPctAsText(); }
    public void leggTilUtbytte(double belop) { addDividend(belop); }
    public void leggTilTransaksjon(double verdi, double oppdatertAntall, double kurs, double resultat, double totaleAvgifter) {
        addTransaction("", verdi < 0 ? "KJØPT" : "SALG", verdi, oppdatertAntall, kurs, resultat, totaleAvgifter);
    }
}