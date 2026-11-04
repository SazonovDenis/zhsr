package kz.zhsr.app.dto;

/**
 * Запрос страницы списка администрирования: {@code ?search=&page=&size=&sort=&desc=}.
 *
 * @param search строка поиска: подстрока в текстовых полях и в тексте ссылок, без учёта регистра; пусто - все
 * @param page   номер страницы с 0; null - 0
 * @param size   строк на странице; null - {@link #DEFAULT_SIZE}, больше {@link #MAX_SIZE} - ошибка
 * @param sort   поле сортировки; null - порядок по умолчанию
 * @param desc   по убыванию; параметра нет - false (Boolean, а не boolean: без параметра Spring не разберёт null в boolean)
 */
public record AdminQuery(String search, Integer page, Integer size, String sort, Boolean desc) {

    public AdminQuery {
        desc = Boolean.TRUE.equals(desc);
    }

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;

    public int pageOrZero() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
