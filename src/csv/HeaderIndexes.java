package csv;

public class HeaderIndexes {
    public int transactionId = -1;
    public int securityName = -1;
    public int securityType = -1;
    public int isin = -1;
    public int transactionType = -1;
    public int amount = -1;
    public int quantity = -1;
    public int price = -1;
    public int result = -1;
    public int totalFees = -1;
    public int purchaseValue = -1;
    public int tradeDate = -1;
    public int cancellationDate = -1;
    public int portfolioId = -1;
    public int transactionText = -1;
    public int cashBalance = -1;
    public int transactionCurrency = -1;
    public int amountCurrency = -1;
    public int resultCurrency = -1;
    public int feeCurrency = -1;

    public boolean hasRequiredColumns() {
        return securityName >= 0 && transactionType >= 0;
    }
}