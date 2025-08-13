package marvin.com.br.diariobordomaquinario.DAO;

import androidx.room.*;

import marvin.com.br.diariobordomaquinario.Model.EncarregadoModel;
import marvin.com.br.diariobordomaquinario.Model.RegistroHorasMaquinasModel;


@Database(entities = {RegistroHorasMaquinasModel.class, EncarregadoModel.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RegistroHorasMaquinasDAO registroHorasMaquinasDao();
    public abstract EncarregadoDAO encarregadoDAO();
}
