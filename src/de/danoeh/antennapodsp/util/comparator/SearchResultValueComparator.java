package de.danoeh.antennapodsp.util.comparator;

import de.danoeh.antennapodsp.feed.SearchResult;

import java.util.Comparator;

public class SearchResultValueComparator implements Comparator<SearchResult> {

    @Override
    public int compare(SearchResult lhs, SearchResult rhs) {
        return rhs.getValue() - lhs.getValue();
    }

}
