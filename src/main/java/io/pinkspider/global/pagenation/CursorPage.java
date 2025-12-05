package io.pinkspider.global.pagenation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CursorPage<N> {

    private N cursorNo;
    private int pageSize = 10;
    private boolean isPaging = false;
}
