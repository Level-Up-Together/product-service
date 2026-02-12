package io.pinkspider.global.pagenation;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class Page {

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;

    @Builder.Default
    private int offset = 0;

    @Builder.Default
    private int limit = 0;

    @Builder.Default
    private boolean isPaging = false;

    public int getOffset() {
        return ((this.page) * this.size);
    }

    public int getLimit() {
        return this.size;
    }

    public boolean isPaging() {
        return this.isPaging;
    }
}
