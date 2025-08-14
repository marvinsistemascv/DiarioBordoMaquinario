package marvin.com.br.diariobordomaquinario.Model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Entity(tableName = "registro_horas_maquinas_model")
@Data
public class RegistroHorasMaquinasModel {

    @PrimaryKey(autoGenerate = true)
    public Integer id;
    public Integer cod_maquina;
    public Double qtd_horas_inicio;
    public Double qtd_horas_final;
    public Double total_horas_trabalhadas;
    public String data_inicio;
    public String hora_inicio;
    public String data_termino;
    public String hora_termino;
    public String maquina;
    public String operador;
    public String encarregado;
    public String sit;
    public String local_obra;

}
