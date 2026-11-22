package kz.zhsr.app.dto.trip;

import kz.zhsr.app.dto.ref.UserRef;
import kz.zhsr.app.dto.ref.WagonRef;
import kz.zhsr.app.dto.wagon.ResponsibleCandidate;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ответственные за вагоны рейса (Ф-РЕЙ-08): вагоны поезда рейса с ответственными слоя layer и кого можно назначить.
 */
@Getter
@Setter
public class TripResponsibles {
    /** рейс (trip.id) */
    private long trip;
    private long tripStatusType;
    /** что показано: TripResponsibleLayer - вычисляемое */
    private int layer;
    /** показан план: изменения записываются планом, без времени изменения - вычисляемое */
    private boolean plan;
    /** станция, сотрудники депо которой могут быть ответственными; null - только бригада рейса - вычисляемое */
    private Long station;
    /** почему пользователь не может менять ответственных; null - может - вычисляемое */
    private String readonlyReason;
    /** вагоны поезда рейса, ответственных которых можно менять, по позиции в поезде */
    private List<Wagon> wagons = List.of();
    /** кого можно назначить: бригада рейса, затем сотрудники депо станции; по ФИО */
    private List<ResponsibleCandidate> candidates = List.of();

    /**
     * Вагон поезда рейса.
     */
    @Getter
    @Setter
    public static class Wagon {
        private WagonRef wagon;
        /** позиция в поезде */
        private int seq;
        /** начало показанных ответственных: записи факта или плана; null - ответственных нет - вычисляемое */
        private OffsetDateTime dbeg;
        /** показанные ответственные: факт или план слоя layer - вычисляемое */
        private List<UserRef> usrs = List.of();
    }
}
