package io.pinkspider.global.pagenation;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class PageAdapter<T> {

    private int totalPages;
    private long totalElements;
    private int number;
    private int numberOfElements;
    private int size;
    private boolean last;
    private boolean first;
    private boolean empty;
    private List<T> content;

    @Builder
    protected PageAdapter(long totalElements, int number, int size, List<T> content) {
        this.totalPages = (int) Math.ceil(totalElements / (double) size);
        this.totalElements = totalElements;
        this.number = number;
        this.size = size;
        this.content = content;
    }

    public int getNumberOfElements() {
        return content.size();
    }

    public boolean isLast() {
        return number == totalPages;
    }

    public boolean isFirst() {
        return number == 1;
    }

    public boolean isEmpty() {
        int numberOfElement = getNumberOfElements();

        return numberOfElement == 0;
    }
}
