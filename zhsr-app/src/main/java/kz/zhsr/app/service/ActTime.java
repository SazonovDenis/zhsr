package kz.zhsr.app.service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Время акта, которое указал пользователь: не в будущем и не раньше чем сутки назад.
 */
final class ActTime {

    private static final Duration MAX_AGE = Duration.ofDays(1);

    // часы телефона и сервера расходятся: столько будущего считается погрешностью
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private ActTime() {
    }

    /**
     * @param time время акта; null - сейчас
     * @return проверенное время
     * @throws ApiException 400 - время в будущем или раньше чем сутки назад
     */
    static OffsetDateTime check(OffsetDateTime time) {
        OffsetDateTime now = OffsetDateTime.now();
        if (time == null) {
            return now;
        }
        if (time.isAfter(now.plus(CLOCK_SKEW))) {
            throw ApiException.badRequest("Время акта не может быть в будущем");
        }
        if (time.isBefore(now.minus(MAX_AGE))) {
            throw ApiException.badRequest("Время акта не может быть раньше чем сутки назад");
        }
        return time.isAfter(now) ? now : time;
    }

}
