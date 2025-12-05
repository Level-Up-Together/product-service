package io.pinkspider.global.graphql.fetcher.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.InputArgument;
import io.pinkspider.global.graphql.dto.query.ShowDto;
import io.pinkspider.global.graphql.service.ShowsService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@DgsComponent
@RequiredArgsConstructor
public class ShowQuery {

    private final ShowsService showsService;

    @DgsData(parentType = "QueryResolver", field = "shows")
    public List<ShowDto> findShows(@InputArgument("titleFilter") String titleFilter) {
        if (titleFilter == null) {
            return showsService.shows();
        }

        return showsService.shows()
            .stream()
            .filter(s -> s.getTitle().contains(titleFilter)).collect(Collectors.toList());
    }
}

