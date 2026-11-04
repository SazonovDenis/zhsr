package kz.zhsr.app.dto;

import java.time.OffsetDateTime;

/**
 * Проводник для набора в бригаду рейса (диалог «Проводники…»).
 *
 * @param usr       usr.id
 * @param name      фамилия и инициалы
 * @param senior    категория «старший проводник»
 * @param assigned  уже в бригаде этого рейса
 * @param busyTrain номер поезда рейса, из которого проводник освободится к отправлению; null - свободен
 * @param busyName  маршрут этого рейса; null - свободен
 * @param busyUntil когда проводник освободится; null - свободен
 */
public record ConductorCandidate(long usr, String name, boolean senior, boolean assigned, String busyTrain,
                                 String busyName, OffsetDateTime busyUntil) {
}
