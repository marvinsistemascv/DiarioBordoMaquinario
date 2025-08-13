package marvin.com.br.diariobordomaquinario.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DataHora {

    public static String data_atual() {
        String data;
        Date dataSistema = new Date();
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd");
        data = formato.format(dataSistema);
        return data;
    }

    public static String pegar_hora() {
        // hora
        String hora;
        Date dataSistema = new Date();
        SimpleDateFormat formato = new SimpleDateFormat("HH:mm:ss");
        hora = formato.format(dataSistema);
        return hora;
    }
}
