package com.apply.diarypic.util.service;

import com.apply.diarypic.util.dto.DailyQuoteResponse;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class UtilService {

    private final Map<DayOfWeek, String> dailyQuotes = new HashMap<>();

    public UtilService() {
        dailyQuotes.put(DayOfWeek.MONDAY, "지금 이 순간이 내일의 추억이 되도록, 사진 한 장을 남겨보세요.");
        dailyQuotes.put(DayOfWeek.TUESDAY, "평범한 오늘도 기록해두면 특별한 이야기가 되어 돌아옵니다.");
        dailyQuotes.put(DayOfWeek.WEDNESDAY, "사진 한 장이 당신의 하루를 말없이 들려줄 거예요.");
        dailyQuotes.put(DayOfWeek.THURSDAY, "소중한 순간을 놓치지 않도록, 지금 바로 올려볼까요?");
        dailyQuotes.put(DayOfWeek.FRIDAY, "작은 순간도 기억으로 남길 때 비로소 빛나기 시작합니다.");
        dailyQuotes.put(DayOfWeek.SATURDAY, "오늘 찍은 한 장의 사진으로 내일은 더 다정해질 거예요.");
        dailyQuotes.put(DayOfWeek.SUNDAY, "과거와 미래가 만나는 그곳, 사진 속에서 이야기해요.");
    }

    public DailyQuoteResponse getDailyQuote() {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeekEnum = today.getDayOfWeek();
        int dayOfWeekValue = dayOfWeekEnum.getValue();
        String quote = dailyQuotes.getOrDefault(dayOfWeekEnum, "오늘 하루도 사진과 함께 소중한 기억을 만들어보세요.");

        return new DailyQuoteResponse(dayOfWeekValue, quote);
    }
}