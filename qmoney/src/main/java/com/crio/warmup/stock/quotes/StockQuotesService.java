package com.crio.warmup.stock.quotes;

import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;

import java.time.LocalDate;
import java.util.List;

public interface StockQuotesService {
  List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to) throws StockQuoteServiceException;
}
