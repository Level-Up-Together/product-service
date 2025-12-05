package io.pinkspider.global.graphql.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowDto {

    private Integer id;

    private String title;

    private Integer releaseYear;

//    public static ShowDto.Builder newBuilder() {
//        return new Builder();
//    }
//
//    public static class Builder {
//
//        private Integer id;
//
//        private String title;
//
//        private Integer releaseYear;
//
//        public ShowDto build() {
//            ShowDto result = new ShowDto();
//            result.id = this.id;
//            result.title = this.title;
//            result.releaseYear = this.releaseYear;
//            return result;
//        }
//
//        public ShowDto.Builder id(Integer id) {
//            this.id = id;
//            return this;
//        }
//
//        public ShowDto.Builder title(String title) {
//            this.title = title;
//            return this;
//        }
//
//        public ShowDto.Builder releaseYear(Integer releaseYear) {
//            this.releaseYear = releaseYear;
//            return this;
//        }
//    }
}
