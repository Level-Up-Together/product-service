package io.pinkspider.global.pagenation;

import io.pinkspider.global.enums.SortType;

public interface Pageable {

    int getPage();

    void setPage(int page);

    int getLimit();

    void setLimit(int limit);

    SortType getSortType();

    void setSortType(SortType type);

    long getOffset();


}
