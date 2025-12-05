package io.pinkspider.global.graphql.service;

import io.pinkspider.global.graphql.dto.query.ShowDto;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShowsService {

    public List<ShowDto> shows() {
        return Arrays.asList(
            ShowDto.builder().id(1).title("Stranger Things").releaseYear(2016).build(),
            ShowDto.builder().id(2).title("Ozark").releaseYear(2017).build(),
            ShowDto.builder().id(3).title("The Crown").releaseYear(2016).build(),
            ShowDto.builder().id(4).title("Dead to Me").releaseYear(2019).build(),
            ShowDto.builder().id(5).title("Orange is the New Black").releaseYear(2013).build()
        );
    }
}
