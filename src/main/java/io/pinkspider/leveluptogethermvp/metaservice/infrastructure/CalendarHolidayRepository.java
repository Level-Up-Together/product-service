package io.pinkspider.leveluptogethermvp.metaservice.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.CalendarHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarHolidayRepository extends JpaRepository<CalendarHoliday, Integer> {

    @Query(value = "WITH A AS (SELECT year\n"
        + "                        , mmdd\n"
        + "                   FROM calendar_holiday\n"
        + "                   WHERE year = :year\n"
        + "                     AND mmdd >= :mmdd\n"
        + "                     AND is_holiday = 'no'\n"
        + "                   UNION ALL\n"
        + "                   SELECT year\n"
        + "                        , mmdd\n"
        + "                   FROM calendar_holiday\n"
        + "                   WHERE year = :year + 1\n"
        + "                     AND mmdd >= '0101'\n"
        + "                     AND is_holiday = 'no')\n"
        + "        SELECT (SELECT is_holiday FROM calendar_holiday WHERE year = :year AND mmdd = :mmdd) isHoliday\n"
        + "             , (SELECT year_count FROM calendar_holiday WHERE year = :year AND mmdd = :mmdd) yearCount\n"
        + "             , max(case when B.day = 1 then concat(B.year, B.mmdd) end)                          business1Day\n"
        + "             , max(case when B.day = 2 then concat(B.year, B.mmdd) end)                          business2Day\n"
        + "             , max(case when B.day = 3 then concat(B.year, B.mmdd) end)                          business3Day\n"
        + "             , max(case when B.day = 4 then concat(B.year, B.mmdd) end)                          business4Day\n"
        + "             , max(case when B.day = 5 then concat(B.year, B.mmdd) end)                          business5Day\n"
        + "             , max(case when B.day = 6 then concat(B.year, B.mmdd) end)                          business6Day\n"
        + "             , max(case when B.day = 7 then concat(B.year, B.mmdd) end)                          business7Day\n"
        + "        FROM (SELECT A.year\n"
        + "                   , A.mmdd\n"
        + "                   , row_number() over (order by A.year, A.mmdd) day\n"
        + "              FROM A\n"
        + "              LIMIT 7) B"
        , nativeQuery = true)
    public CalendarHoliday findCalendarHolidayBusinessInfo(String year, String mmdd);

    @Query(value = "SELECT max(concat(C.year, C.mmdd)) AS business1Day\n"
        + "        FROM (SELECT IFNULL(A.year, B.year) AS year\n"
        + "                   , IFNULL(A.mmdd, B.mmdd) AS mmdd\n"
        + "              FROM (SELECT min(year) as year\n"
        + "                         , min(mmdd) as mmdd\n"
        + "                    FROM calendar_holiday\n"
        + "                    WHERE 1 = 1\n"
        + "                      AND year = :year\n"
        + "                      AND mmdd >= :mmdd\n"
        + "                      AND is_holiday = 'no') A\n"
        + "                 , (SELECT min(year) as year\n"
        + "                         , min(mmdd) as mmdd\n"
        + "                    FROM calendar_holiday\n"
        + "                    WHERE 1 = 1\n"
        + "                      AND year = :year + 1\n"
        + "                      AND mmdd >= '0101'\n"
        + "                      AND is_holiday = 'no') B) C"
        , nativeQuery = true)
    public CalendarHoliday findCalendarHolidayNextOneBusinessDayInfo(String year, String mmdd);

    @Query(value = " WITH A AS (SELECT year\n"
        + "                        , mmdd\n"
        + "                   FROM calendar_holiday\n"
        + "                   WHERE year = :year - 1\n"
        + "                     AND mmdd <= '1231'\n"
        + "                     AND is_holiday = 'no'\n"
        + "                   UNION ALL\n"
        + "                   SELECT year\n"
        + "                        , mmdd\n"
        + "                   FROM calendar_holiday\n"
        + "                   WHERE year = :year\n"
        + "                     AND mmdd <= :mmdd\n"
        + "                     AND is_holiday = 'no')\n"
        + "        SELECT min(case when B.day = 1 then concat(B.year, B.mmdd) end) AS business1Day\n"
        + "             , min(case when B.day = 2 then concat(B.year, B.mmdd) end) AS business2Day\n"
        + "             , min(case when B.day = 3 then concat(B.year, B.mmdd) end) AS business3Day\n"
        + "             , min(case when B.day = 4 then concat(B.year, B.mmdd) end) AS business4Day\n"
        + "             , min(case when B.day = 5 then concat(B.year, B.mmdd) end) AS business5Day\n"
        + "             , min(case when B.day = 6 then concat(B.year, B.mmdd) end) AS business6Day\n"
        + "             , min(case when B.day = 7 then concat(B.year, B.mmdd) end) AS business7Day\n"
        + "        FROM (SELECT A.year\n"
        + "                   , A.mmdd\n"
        + "                   , row_number() over (order by A.year desc, A.mmdd desc) AS day\n"
        + "              FROM A\n"
        + "              LIMIT 7) B"
        , nativeQuery = true)
    public CalendarHoliday findCalendarHolidayPreviousBusinessDayInfo(String year, String mmdd);
}
