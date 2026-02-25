package com.deriklima.retronap.search;

import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SearchResult;
import java.util.List;

public interface SearchService {

  List<SearchResult> searchForShares(SearchParameters params);

  List<SearchResult> searchForShares(ResumeParameters params);
}
