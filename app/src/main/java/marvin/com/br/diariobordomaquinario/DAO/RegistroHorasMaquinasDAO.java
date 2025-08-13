package marvin.com.br.diariobordomaquinario.DAO;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import marvin.com.br.diariobordomaquinario.Model.RegistroHorasMaquinasModel;

@Dao
public interface RegistroHorasMaquinasDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void inserir(RegistroHorasMaquinasModel c);

    @Query("SELECT * FROM registro_horas_maquinas_model where sit = 'em curso'")
    List<RegistroHorasMaquinasModel> pegar_corridas_nao_sincronizadas();

    @Query("SELECT * FROM registro_horas_maquinas_model WHERE cod_maquina = :cod_maquina ORDER BY id DESC LIMIT 1")
    RegistroHorasMaquinasModel pegarUltimaCorridaMaquina(Integer cod_maquina);

    @Query("SELECT * FROM registro_horas_maquinas_model where sit != 'sincronizado'")
    List<RegistroHorasMaquinasModel> pegar_corridas_sincronizadas();

    @Query("UPDATE registro_horas_maquinas_model SET sit = :novoStatus WHERE id = :id")
    void atualizar_status(String novoStatus, int id);

}
