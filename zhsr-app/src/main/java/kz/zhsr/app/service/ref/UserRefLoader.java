package kz.zhsr.app.service.ref;

import kz.kis.kiscore.model.DbRec;
import kz.kis.kisdbtools.ref.RefLoader;
import kz.kis.kisdbtools.repository.Db;
import kz.zhsr.app.auth.CurrentUser;
import kz.zhsr.app.dto.ref.UserRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Сотрудник: фамилия и инициалы.
 */
@Component
class UserRefLoader extends RefLoader<UserRef> {

    private final Db db;

    UserRefLoader(Db db) {
        this.db = db;
    }

    @Override
    public Class<UserRef> refClass() {
        return UserRef.class;
    }

    @Override
    protected List<DbRec> loadBlock(List<Long> ids) throws Exception {
        return db.loadList("usr", Map.of("id", ids));
    }

    @Override
    protected void fill(UserRef ref, DbRec rec) {
        ref.setName(CurrentUser.shortName(rec.getString("full_name")));
    }

}
