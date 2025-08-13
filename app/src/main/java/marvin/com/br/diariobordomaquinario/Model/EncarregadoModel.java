package marvin.com.br.diariobordomaquinario.Model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Entity(tableName = "encarregado_model")
@Data
public class EncarregadoModel {

    @PrimaryKey(autoGenerate = true)
    public Integer id;
    public String nome;
}
