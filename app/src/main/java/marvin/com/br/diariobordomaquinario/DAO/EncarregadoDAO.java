package marvin.com.br.diariobordomaquinario.DAO;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import marvin.com.br.diariobordomaquinario.Model.EncarregadoModel;
@Dao
public interface EncarregadoDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void inserir(EncarregadoModel e);

    @Query("SELECT * FROM encarregado_model where id =1")
    EncarregadoModel pegar_encarregado();

    @Query("UPDATE encarregado_model SET nome = :novoNome WHERE id = 1")
    void atualizar_nome(String novoNome);
}
