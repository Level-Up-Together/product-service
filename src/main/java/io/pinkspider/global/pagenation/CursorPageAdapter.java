package io.pinkspider.global.pagenation;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class CursorPageAdapter<T, N> {

    private int numberOfElements;
    private int size;
    private boolean hasNext;
    private boolean empty;
    private N nextCursorNumber;
    private List<T> content;

    @Builder
    protected CursorPageAdapter(int size, N nextCursorNumber, List<T> content) {
        this.size = size;
        this.nextCursorNumber = nextCursorNumber;
        this.content = content;
    }

    public int getNumberOfElements() {
        return content.size();
    }

    public boolean isHasNext() {
        int numberOfElement = getNumberOfElements();

        return numberOfElement == size;
    }

    public boolean isEmpty() {
        int numberOfElement = getNumberOfElements();

        return numberOfElement == 0;
    }
}
