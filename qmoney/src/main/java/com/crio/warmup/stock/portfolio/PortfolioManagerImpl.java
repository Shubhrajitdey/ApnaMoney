
package com.crio.warmup.stock.portfolio;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {
  private RestTemplate restTemplate;
  private StockQuotesService stockQuotesService;
  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  @Deprecated
  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }


  //TODO: CRIO_TASK_MODULE_REFACTOR
  // 1. Now we want to convert our code into a module, so we will not call it from main anymore.
  //    Copy your code from Module#3 PortfolioManagerApplication#calculateAnnualizedReturn
  //    into #calculateAnnualizedReturn function here and ensure it follows the method signature.
  // 2. Logic to read Json file and convert them into Objects will not be required further as our
  //    clients will take care of it, going forward.

  // Note:
  // Make sure to exercise the tests inside PortfolioManagerTest using command below:
  // ./gradlew test --tests PortfolioManagerTest

  //CHECKSTYLE:OFF

  public PortfolioManagerImpl(StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
  }
  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,LocalDate endDate) throws StockQuoteServiceException{
      List<AnnualizedReturn> annualizedReturnsList = new ArrayList<>();
      for (PortfolioTrade pt : portfolioTrades) {
          List<Candle> tiingoCandlesList = getStockQuote(pt.getSymbol(), pt.getPurchaseDate(), endDate);
          Double openingPrice = getOpeningPriceOnStartDate(tiingoCandlesList);
          Double closingPrice = getClosingPriceOnEndDate(tiingoCandlesList);
          annualizedReturnsList.add(calculateAnnualizedReturns(endDate, pt, openingPrice, closingPrice));
    }
    annualizedReturnsList.sort(getComparator());
    return annualizedReturnsList;
  }
  
  static Double getOpeningPriceOnStartDate(List<Candle> candles) {
    return candles.get(0).getOpen();
  }

  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
    return candles.get(candles.size() - 1).getClose();
  }
  
  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate,
      PortfolioTrade trade, Double buyPrice, Double sellPrice) {
    Double totalReturn = (sellPrice - buyPrice) / buyPrice;
    Double noOfYears = ChronoUnit.DAYS.between(trade.getPurchaseDate(), endDate) / 365.24;
    Double annualizedReturns = Math.pow((1.0 + totalReturn), (1.0 / noOfYears)) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturns, totalReturn);
  }
  
  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws StockQuoteServiceException {
    return stockQuotesService.getStockQuote(symbol, from, to);
  }
  
  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(List<PortfolioTrade> portfolioTrades,
      LocalDate endDate, int numThreads) throws InterruptedException, StockQuoteServiceException {
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    List<AnnualizedReturn> annualizedReturnsList = new ArrayList<>();
    List<Future<AnnualizedReturn>> futureAnnualizedList = new ArrayList<Future<AnnualizedReturn>>();
    List<AnnualizedReturnTask> annualizedReturnTasksList = new ArrayList<>();
    for (PortfolioTrade pt : portfolioTrades) {
      annualizedReturnTasksList.add(new AnnualizedReturnTask(pt, stockQuotesService, endDate));
    }
    futureAnnualizedList = executor.invokeAll(annualizedReturnTasksList);
    try{
      for(Future<AnnualizedReturn> futureAnnualized : futureAnnualizedList){
        annualizedReturnsList.add(futureAnnualized.get());
      }
    }catch(InterruptedException|ExecutionException e){
      throw new StockQuoteServiceException(e.getMessage());
    }
    executor.shutdown();  
    annualizedReturnsList.sort(getComparator());
    return annualizedReturnsList;
  }
}
