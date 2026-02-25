package com.deriklima.retronap.search;

import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SearchResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

  private final List<ShareProvider> shareProviders;

  public SearchServiceImpl(List<ShareProvider> shareProviders) {
    this.shareProviders = shareProviders;
  }

  @Override
  public List<SearchResult> searchForShares(SearchParameters params) {
    List<SearchResult> allResults = new ArrayList<>();
    for (ShareProvider provider : shareProviders) {
      List<SearchResult> results = provider.searchForShares(params);
      if (results != null) {
        allResults.addAll(results);
      }
    }
    return allResults;
  }

  @Override
  public List<SearchResult> searchForShares(ResumeParameters params) {
    List<SearchResult> allResults = new ArrayList<>();
    for (ShareProvider provider : shareProviders) {
      List<SearchResult> results = provider.searchForShares(params);
      if (results != null) {
        allResults.addAll(results);
      }
    }
    return allResults;
  }
}
