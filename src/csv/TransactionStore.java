package csv;

import model.Events;
import model.Security;

import java.util.*;

public class TransactionStore {

    private final Map<String, String> renamedSecurityIsin = new LinkedHashMap<>();

    private final ArrayList<Security> securities = new ArrayList<>();
    private final Map<String, Security> securitiesByKey = new LinkedHashMap<>();
    private final Map<String, String> canonicalSecurityNameByIsin = new LinkedHashMap<>();

    private final ArrayList<Events.UnitEvent> unitEvents = new ArrayList<>();
    private final ArrayList<Events.CashEvent> cashEvents = new ArrayList<>();
    private final ArrayList<Events.PortfolioCashSnapshot> portfolioCashSnapshots = new ArrayList<>();

    private int loadedCsvFileCount = 0;
    private int loadedTransactionRowCount = 0;

    public Security getOrCreateSecurity(String name, String isin) {
        String key = (isin == null || isin.isBlank()) ? name : isin;
        return securitiesByKey.computeIfAbsent(key, k -> {
            Security security = new Security(name, isin);
            securities.add(security);
            return security;
        });
    }

    public void addUnitEvent(Events.UnitEvent event) {
        unitEvents.add(event);
    }

    public void addCashEvent(Events.CashEvent event) {
        cashEvents.add(event);
    }

    public void addPortfolioCashSnapshot(Events.PortfolioCashSnapshot snapshot) {
        portfolioCashSnapshots.add(snapshot);
    }

    public void incrementTransactionRowCount() {
        loadedTransactionRowCount++;
    }

    public void setLoadedCsvFileCount(int count) {
        loadedCsvFileCount = Math.max(0, count);
    }

    public void rememberCanonicalSecurityName(String originalIsin, String canonicalIsin, String securityName) {
        if (securityName == null || securityName.isBlank() || canonicalIsin == null || canonicalIsin.isBlank()) {
            return;
        }
        String norm = canonicalIsin.trim().toUpperCase(Locale.ROOT);
        canonicalSecurityNameByIsin.putIfAbsent(norm, securityName);
    }

    public void rememberRenamedSecurityIsin(String oldIsin, String newIsin) {
        if (oldIsin == null || newIsin == null) {
            return;
        }

        String oldNorm = oldIsin.trim().toUpperCase(Locale.ROOT);
        String newNorm = newIsin.trim().toUpperCase(Locale.ROOT);
        if (oldNorm.isBlank() || newNorm.isBlank() || oldNorm.equals(newNorm)) {
            return;
        }

        renamedSecurityIsin.putIfAbsent(oldNorm, newNorm);
    }

    public List<Security> getSecurities() {
        return new ArrayList<>(securities);
    }

    public List<Events.UnitEvent> getUnitEvents() {
        return new ArrayList<>(unitEvents);
    }

    public List<Events.CashEvent> getCashEvents() {
        return new ArrayList<>(cashEvents);
    }

    public List<Events.PortfolioCashSnapshot> getPortfolioCashSnapshots() {
        return new ArrayList<>(portfolioCashSnapshots);
    }

    public Map<String, String> getCanonicalSecurityNameByIsin() {
        return new LinkedHashMap<>(canonicalSecurityNameByIsin);
    }

    public Map<String, String> getRenamedSecurityIsin() {
        return Collections.unmodifiableMap(renamedSecurityIsin);
    }

    public int getLoadedCsvFileCount() {
        return loadedCsvFileCount;
    }

    public int getLoadedTransactionRowCount() {
        return loadedTransactionRowCount;
    }

    public double getCurrentCashHoldings() {
        if (portfolioCashSnapshots.isEmpty()) {
            double cash = 0.0;
            for (Events.CashEvent event : cashEvents) {
                cash += event.cashDelta();
            }
            return cash;
        }

        LinkedHashMap<String, Events.PortfolioCashSnapshot> latestByPortfolio = new LinkedHashMap<>();
        for (Events.PortfolioCashSnapshot snapshot : portfolioCashSnapshots) {
            if (snapshot == null || snapshot.portfolioId() == null || snapshot.portfolioId().isBlank()) {
                continue;
            }

            Events.PortfolioCashSnapshot existing = latestByPortfolio.get(snapshot.portfolioId());
            if (existing == null
                    || snapshot.tradeDate().isAfter(existing.tradeDate())
                    || (snapshot.tradeDate().equals(existing.tradeDate()) && snapshot.sortId() >= existing.sortId())) {
                latestByPortfolio.put(snapshot.portfolioId(), snapshot);
            }
        }

        double authoritativePortfolioCash = 0.0;
        for (Events.PortfolioCashSnapshot snapshot : latestByPortfolio.values()) {
            authoritativePortfolioCash += snapshot.balance();
        }

        return authoritativePortfolioCash;
    }
}