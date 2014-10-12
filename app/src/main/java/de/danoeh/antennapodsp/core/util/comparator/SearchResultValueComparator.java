package de.danoeh.antennapodsp.core.util.comparator;

import de.danoeh.antennapodsp.core.feed.SearchResult;

import java.util.Comparator;

public class SearchResultValueComparator implements Comparator<SearchResult> {

    @Override
    public int compare(SearchResult lhs, SearchResult rhs) {
        return rhs.getValue() - lhs.getValue();
    }

}
