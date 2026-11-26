package kz.zhsr.app.dto.trip;

import kz.kis.kisdbtools.ref.Ref;
import kz.zhsr.app.dto.train.WagonProblem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Готовность рейса к отправлению (у рейса в статусе «Выполняется» - по текущему состоянию) - псевдо-ссылка по id
 * рейса: данные пунктов готовности заполняет TripReadinessLoader вторым проходом RefResolver. Тексты пунктов и вывод
 * «выполнен / не выполнен» строит фронт.
 */
@Getter
@Setter
public class TripReadiness extends Ref {
    /** поезд назначен */
    private boolean train;
    /** номера вагонов с ожидающими планами, которые надо отметить или отменить до отправления */
    private List<String> pending = List.of();
    /** номера вагонов прицепок и отцепок, выполненных фактом до отправления: вне рейса после планового отправления
     * предыдущего рейса поезда и самого рейса не позже его отправления */
    private List<String> done = List.of();
    /** сколько вагонов едет в поезде на отправлении (в пути - сейчас) */
    private int riding;
    /** номера едущих вагонов не на станции отправления (в пути - не в этом рейсе) */
    private List<String> away = List.of();
    /** неготовность вагонов рейса: едущих на отправлении (в пути - сейчас) и прицепляемых планом на остановках */
    private List<WagonProblem> problems = List.of();
    /** назначен ЛНП */
    private boolean lnp;
    /** назначен ПЭМ */
    private boolean pem;
    /** номера вагонов, у которых не выполнена норма проводников (TripNorm) */
    private List<String> normNotOk = List.of();

    /** сколько позиций описей экипировки в недостаче: по последней описи каждого едущего вагона по каждому шаблону */
    private int equipShortage;

    /** номера едущих вагонов, у которых опись экипировки не проводилась ни разу */
    private List<String> equipNoList = List.of();

    public TripReadiness() {
    }

    public TripReadiness(long id) {
        super(id);
    }
}
